package com.mcd.ledger.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcd.ledger.common.BizException;
import com.mcd.ledger.common.ErrorCode;
import com.mcd.ledger.config.McpProperties;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MCP 客户端（设计 §3.4/§3.5）：
 * Streamable HTTP POST JSON-RPC 调 mcp.mcd.cn；
 * Header Authorization: Bearer ${MCD_MCP_TOKEN}；
 * Guava 令牌桶 600/min；
 * 遇 429 指数退避 500ms→1s→2s 最多 3 次；
 * 遇 401 转 MCP_AUTH_FAILED，其他上游异常转 MCP_UPSTREAM_ERROR。
 *
 * 白名单校验由适配层（OrderAdapter/MenuAdapter 等）在调用前完成；本类只负责传输。
 * Bean 装配由 McpClientConfig 按 mcd.sandbox.enabled 选择：真实 HTTP 实现（sandbox=false）
 * 或 SandboxMcpClient 沙盒顶替（sandbox=true，默认）。本类为普通类，可独立 new（单测用）。
 */
public class McpClient {

    private static final Logger log = LoggerFactory.getLogger(McpClient.class);
    private static final long[] BACKOFF_MS = {500L, 1000L, 2000L};

    private final McpProperties props;
    private final McpRateLimiter rateLimiter;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http;
    private final AtomicLong idGen = new AtomicLong(1);

    public McpClient(McpProperties props, McpRateLimiter rateLimiter) {
        this.props = props;
        // 依据配置速率重建限流器（默认 600/min）
        this.rateLimiter = props.getRateLimitPerMin() == 600
                ? rateLimiter
                : new McpRateLimiter(props.getRateLimitPerMin());
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** 调用指定 MCP 工具，返回 result 节点。 */
    public JsonNode call(String tool, JsonNode args) {
        if (props.getToken() == null || props.getToken().isBlank()) {
            throw BizException.of(ErrorCode.MCP_UPSTREAM_ERROR, "MCP Token 未配置，无法调用 " + tool);
        }
        rateLimiter.acquire();

        String requestBody;
        try {
            ObjectNode root = mapper.createObjectNode();
            root.put("jsonrpc", "2.0");
            root.put("id", idGen.getAndIncrement());
            ObjectNode params = root.putObject("params");
            params.put("name", tool);
            params.putIfAbsent("arguments", args == null ? mapper.createObjectNode() : args);
            requestBody = mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw BizException.of(ErrorCode.MCP_UPSTREAM_ERROR, "构造 MCP 请求失败: " + e.getMessage());
        }

        int attempt = 0;
        while (true) {
            int status = -1;
            String respBody = null;
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(props.getServerUrl()))
                        .timeout(Duration.ofSeconds(30))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + props.getToken())
                        .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                        .build();
                HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
                status = resp.statusCode();
                respBody = resp.body();
            } catch (IOException e) {
                throw BizException.of(ErrorCode.MCP_UPSTREAM_ERROR, "MCP 网络异常: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw BizException.of(ErrorCode.MCP_UPSTREAM_ERROR, "MCP 调用被中断");
            }

            if (status == 401) {
                throw BizException.of(ErrorCode.MCP_AUTH_FAILED, "Token 失效，请重新配置");
            }
            if (status == 429) {
                if (attempt < BACKOFF_MS.length) {
                    long sleep = BACKOFF_MS[attempt++];
                    log.warn("MCP 429 rate-limited, backoff {}ms (attempt {})", sleep, attempt);
                    sleepQuietly(sleep);
                    continue;
                }
                throw BizException.of(ErrorCode.MCP_RATE_LIMITED, "MCP 限流，请稍后重试");
            }
            if (status >= 200 && status < 300) {
                return parseResult(respBody);
            }
            throw BizException.of(ErrorCode.MCP_UPSTREAM_ERROR, "MCP 上游异常 HTTP " + status);
        }
    }

    private JsonNode parseResult(String body) {
        try {
            JsonNode root = mapper.readTree(body);
            if (root.has("error")) {
                JsonNode err = root.get("error");
                int code = err.path("code").asInt(0);
                // JSON-RPC 层鉴权失败
                if (code == -32000 || code == -32001 || code == 401) {
                    throw BizException.of(ErrorCode.MCP_AUTH_FAILED, "Token 失效，请重新配置");
                }
                throw BizException.of(ErrorCode.MCP_UPSTREAM_ERROR,
                        "MCP 返回错误: " + err.path("message").asText());
            }
            JsonNode result = root.get("result");
            return result == null ? mapper.createObjectNode() : result;
        } catch (BizException be) {
            throw be;
        } catch (Exception e) {
            throw BizException.of(ErrorCode.MCP_UPSTREAM_ERROR, "解析 MCP 响应失败: " + e.getMessage());
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
