package com.mcd.plan.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcd.ledger.config.McpProperties;
import com.mcd.ledger.mcp.McpClient;
import com.mcd.ledger.mcp.McpRateLimiter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 沙盒 MCP 客户端（设计 §7.5「只替换最底层数据源」）：
 * 顶替真实 McpClient（同类型 Bean，按 mcd.sandbox.enabled 切换），按 tool 名返回
 * SandboxPlanFixture 预置的真实响应，不触网、不需要 Token。
 * 未预置的 tool 返回空信封，上层适配器 graceful 降级为空结果。
 */
@Component
@ConditionalOnProperty(name = "mcd.sandbox.enabled", havingValue = "true", matchIfMissing = true)
public class SandboxMcpClient extends McpClient {

    private final SandboxPlanFixture fixture;
    private final ObjectMapper mapper = new ObjectMapper();

    public SandboxMcpClient(McpProperties props, McpRateLimiter rateLimiter, SandboxPlanFixture fixture) {
        super(props, rateLimiter);
        this.fixture = fixture;
    }

    @Override
    public JsonNode call(String tool, JsonNode args) {
        JsonNode preset = fixture.resultFor(tool);
        if (preset != null) {
            return preset;
        }
        // 未预置 tool：空信封（success + 空 data），避免报错/触网
        try {
            String empty = "{\"content\":[{\"type\":\"text\",\"text\":"
                    + "\"{\\\"success\\\":true,\\\"code\\\":200,\\\"data\\\":{}}\"}],"
                    + "\"structuredContent\":{\"success\":true,\"code\":200,\"data\":{}}}";
            return mapper.readTree(empty);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }
}
