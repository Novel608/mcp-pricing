package com.mcd.plan.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 沙盒预置 MCP 响应（设计 §7.5 / §2.1）：源自 A1 真实抓取、脱敏（token 已清除）。
 * 按 tool 名返回其 `result` 节点（含 content/structuredContent），供 SandboxMcpClient 顶替数据源。
 * 上层 MenuAdapter/CouponAdapter/PriceAdapter 与真实链路完全共用，业务代码零 if(isSandbox)。
 */
@Component
public class SandboxPlanFixture {

    private final ObjectMapper mapper = new ObjectMapper();
    private volatile Map<String, JsonNode> byTool = Map.of();

    private static final Map<String, String> FILES = Map.of(
            "query-meals", "sandbox/query-meals.json",
            "query-meal-detail", "sandbox/query-meal-detail.json",
            "query-store-coupons", "sandbox/store-coupons.json",
            "calculate-price", "sandbox/calculate-price.json",
            "order-list", "sandbox/order-list.json");

    @PostConstruct
    void load() {
        Map<String, JsonNode> m = new java.util.concurrent.ConcurrentHashMap<>();
        FILES.forEach((tool, path) -> {
            try (InputStream in = new ClassPathResource(path).getInputStream()) {
                JsonNode root = mapper.readTree(in);
                JsonNode result = root.get("result");
                m.put(tool, result != null ? result : root);
            } catch (Exception e) {
                // 缺失的 fixture 不影响启动；该 tool 将返回空信封
            }
        });
        byTool = m;
    }

    /** 返回预置的 result 节点；未预置返回 null。 */
    public JsonNode resultFor(String tool) {
        return byTool.get(tool);
    }
}
