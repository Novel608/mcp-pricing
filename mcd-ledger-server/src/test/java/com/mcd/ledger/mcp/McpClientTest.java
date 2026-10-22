package com.mcd.ledger.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.mcd.ledger.common.BizException;
import com.mcd.ledger.common.ErrorCode;
import com.mcd.ledger.config.McpProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * QA 补测（严过关）：McpClient 韧性（设计 §3.5 / NF-6）。
 * 用 JDK 内置 HttpServer 打桩上游，证明：
 *  429 指数退避 500ms→1s→2s 最多 3 次后抛 MCP_RATE_LIMITED（共 4 次请求）；
 *  429 后恢复即成功；401 → MCP_AUTH_FAILED 不重试；5xx/网络异常 → MCP_UPSTREAM_ERROR；
 *  JSON-RPC 层鉴权失败 → MCP_AUTH_FAILED；Token 缺失 → MCP_UPSTREAM_ERROR 且零请求；
 *  请求确实携带 Authorization: Bearer 头（Token 来自配置/环境变量，代码不产出值）。
 */
class McpClientTest {

    private HttpServer server;
    private McpProperties props;
    private McpClient client;

    /** 记录每次请求的路径与 Authorization 头 */
    private final CopyOnWriteArrayList<String> authHeaders = new CopyOnWriteArrayList<>();
    private final AtomicInteger requestCount = new AtomicInteger(0);

    /** 每个请求按序消费的响应（status, body）；消费完后重复最后一个 */
    private List<int[]> statusPlan = List.of(new int[]{200});
    private List<String> bodyPlan = List.of("{}");

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", this::handle);
        server.start();
        props = new McpProperties();
        props.setServerUrl("http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort());
        props.setToken("test-token-from-env-stand-in");
        client = new McpClient(props, new McpRateLimiter());
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        int n = requestCount.getAndIncrement();
        authHeaders.add(ex.getRequestHeaders().getFirst("Authorization"));
        int status = statusPlan.get(Math.min(n, statusPlan.size() - 1))[0];
        String body = bodyPlan.get(Math.min(n, bodyPlan.size() - 1));
        byte[] out = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, out.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(out);
        }
    }

    private static String rpcOk(String resultJson) {
        return "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":" + resultJson + "}";
    }

    @Test
    @DisplayName("200 正常响应：返回 result 节点，且请求带 Bearer 头")
    void ok_returnsResult_withBearerHeader() {
        statusPlan = List.of(new int[]{200});
        bodyPlan = List.of(rpcOk("{\"orders\":[{\"orderId\":\"MCD-1\"}]}"));
        JsonNode result = client.call("order-list", null);
        assertNotNull(result);
        assertEquals("MCD-1", result.path("orders").path(0).path("orderId").asText());
        assertEquals(1, requestCount.get());
        assertEquals("Bearer test-token-from-env-stand-in", authHeaders.get(0));
    }

    @Test
    @DisplayName("401 → MCP_AUTH_FAILED(401)，不重试（仅 1 次请求）")
    void unauthorized_401_authFailed_noRetry() {
        statusPlan = List.of(new int[]{401});
        bodyPlan = List.of("{\"error\":\"unauthorized\"}");
        BizException ex = assertThrows(BizException.class, () -> client.call("query-order", null));
        assertEquals(ErrorCode.MCP_AUTH_FAILED, ex.getErrorCode());
        assertEquals(401, ErrorCode.MCP_AUTH_FAILED.httpStatus());
        assertEquals(1, requestCount.get(), "401 不应触发退避重试");
    }

    @Test
    @DisplayName("JSON-RPC 层鉴权错误（-32000/-32001）→ MCP_AUTH_FAILED")
    void jsonRpcAuthError_authFailed() {
        statusPlan = List.of(new int[]{200});
        bodyPlan = List.of("{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32001,\"message\":\"token invalid\"}}");
        BizException ex = assertThrows(BizException.class, () -> client.call("query-order", null));
        assertEquals(ErrorCode.MCP_AUTH_FAILED, ex.getErrorCode());
    }

    @Test
    @DisplayName("持续 429：指数退避 500+1000+2000ms，最多 3 次后抛 MCP_RATE_LIMITED（共 4 次请求）")
    void rateLimited_429_backoff3times_thenRateLimited() {
        statusPlan = List.of(new int[]{429});
        bodyPlan = List.of("{\"error\":\"too many requests\"}");
        long t0 = System.nanoTime();
        BizException ex = assertThrows(BizException.class, () -> client.call("order-list", null));
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        assertEquals(ErrorCode.MCP_RATE_LIMITED, ex.getErrorCode());
        assertEquals(429, ErrorCode.MCP_RATE_LIMITED.httpStatus());
        assertEquals(4, requestCount.get(), "首次 + 3 次退避重试 = 4 次请求");
        assertTrue(elapsedMs >= 3500, "退避总时长应 >= 500+1000+2000=3500ms，实际 " + elapsedMs + "ms");
    }

    @Test
    @DisplayName("429 后恢复：第 2 次即成功，仅 1 次退避")
    void rateLimitedThenOk_succeedsAfterOneBackoff() {
        statusPlan = List.of(new int[]{429}, new int[]{200});
        bodyPlan = List.of("{\"error\":\"slow down\"}", rpcOk("{\"ok\":true}"));
        long t0 = System.nanoTime();
        JsonNode result = client.call("order-list", null);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;
        assertEquals(true, result.path("ok").asBoolean());
        assertEquals(2, requestCount.get());
        assertTrue(elapsedMs >= 500, "应至少退避 500ms，实际 " + elapsedMs + "ms");
    }

    @Test
    @DisplayName("500 上游异常 → MCP_UPSTREAM_ERROR(502)，不重试")
    void serverError_500_upstreamError() {
        statusPlan = List.of(new int[]{500});
        bodyPlan = List.of("boom");
        BizException ex = assertThrows(BizException.class, () -> client.call("order-list", null));
        assertEquals(ErrorCode.MCP_UPSTREAM_ERROR, ex.getErrorCode());
        assertEquals(502, ErrorCode.MCP_UPSTREAM_ERROR.httpStatus());
        assertEquals(1, requestCount.get());
    }

    @Test
    @DisplayName("JSON-RPC 业务错误 → MCP_UPSTREAM_ERROR")
    void jsonRpcBusinessError_upstreamError() {
        statusPlan = List.of(new int[]{200});
        bodyPlan = List.of("{\"jsonrpc\":\"2.0\",\"id\":1,\"error\":{\"code\":-32603,\"message\":\"internal\"}}");
        BizException ex = assertThrows(BizException.class, () -> client.call("order-list", null));
        assertEquals(ErrorCode.MCP_UPSTREAM_ERROR, ex.getErrorCode());
    }

    @Test
    @DisplayName("Token 未配置：MCP_UPSTREAM_ERROR 且零请求（不触网）")
    void missingToken_upstreamError_zeroRequests() {
        props.setToken("");
        BizException ex = assertThrows(BizException.class, () -> client.call("order-list", null));
        assertEquals(ErrorCode.MCP_UPSTREAM_ERROR, ex.getErrorCode());
        assertEquals(0, requestCount.get(), "无 Token 时不应发出任何请求");
    }

    @Test
    @DisplayName("连接被拒（上游不可达）→ MCP_UPSTREAM_ERROR")
    void connectionRefused_upstreamError() {
        props.setServerUrl("http://127.0.0.1:1"); // 1 号端口必然拒绝
        BizException ex = assertThrows(BizException.class, () -> client.call("order-list", null));
        assertEquals(ErrorCode.MCP_UPSTREAM_ERROR, ex.getErrorCode());
    }
}
