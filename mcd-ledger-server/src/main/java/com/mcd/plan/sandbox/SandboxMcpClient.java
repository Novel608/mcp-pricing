package com.mcd.plan.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcd.ledger.config.McpProperties;
import com.mcd.ledger.mcp.McpClient;
import com.mcd.ledger.mcp.McpRateLimiter;

/**
 * 沙盒 MCP 客户端（设计 §7.5「只替换最底层数据源」）：
 * 顶替真实 McpClient（由 McpClientConfig 按 mcd.sandbox.enabled 装配），按 tool 名返回
 * SandboxPlanFixture 预置的真实响应，不触网、不需要 Token。
 * 未预置的 tool 返回空信封，上层适配器 graceful 降级为空结果。
 */
public class SandboxMcpClient extends McpClient {

    private final SandboxPlanFixture fixture;
    private final ObjectMapper mapper = new ObjectMapper();

    public SandboxMcpClient(McpProperties props, McpRateLimiter rateLimiter, SandboxPlanFixture fixture) {
        super(props, rateLimiter);
        this.fixture = fixture;
    }

    @Override
    public JsonNode call(String tool, JsonNode args) {
        // 核价：优先用官方菜单抓取值重算的模拟器（保证 demo 数字自洽），失败回退真实抓取快照
        if ("calculate-price".equals(tool)) {
            JsonNode sim = fixture.simulateCalculatePrice(args);
            if (sim != null) {
                return sim;
            }
        }
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
