package com.mcd.plan.sandbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcd.ledger.common.Money;
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

    /**
     * 沙盒核价模拟器（**仅 mcd.sandbox.enabled=true 时由 SandboxMcpClient 调用**）。
     *
     * <p>背景：A1 真实抓取的 `calculate-price` 响应是「某一固定餐品组合」的快照，
     * 直接回放会让 demo 的「选餐小计 ¥196.50 vs 官方核价 ¥33.50」自相矛盾。
     * 故沙盒层以**官方 query-meals 抓取值（currentPrice / originalPrice）**为唯一输入，
     * 按官方响应结构重算小计与 productList —— 扮演的是「数据源」而非「业务估算」：
     * 上层 PriceAdapter / PlanPricingService 代码与真实链路完全一致（零 if(isSandbox)）。
     * 真实模式下本方法根本不会被执行（装配的是真实 McpClient）。
     *
     * @return 模拟的 result 节点；含未知餐品 code 时返回 null（调用方回退到真实抓取快照）
     */
    public JsonNode simulateCalculatePrice(JsonNode args) {
        JsonNode menu = resultFor("query-meals");
        if (menu == null || args == null) {
            return null;
        }
        JsonNode meals = menu.path("structuredContent").path("data").path("meals");
        JsonNode items = args.get("items");
        if (items == null || !items.isArray() || items.isEmpty()) {
            return null;
        }
        long productPrice = 0L;
        long productOriginal = 0L;
        ArrayNode list = mapper.createArrayNode();
        for (JsonNode it : items) {
            String code = it.path("productCode").asText();
            int qty = it.path("quantity").asInt();
            JsonNode m = meals.get(code);
            if (m == null) {
                return null; // 有非菜单餐品 → 不模拟，回退真实抓取快照
            }
            long cur = yuan(m, "currentPrice");
            long orig = m.path("originalPrice").isNull() ? cur : yuan(m, "originalPrice");
            productPrice += cur * qty;
            productOriginal += orig * qty;
            ObjectNode line = list.addObject();
            line.put("productCode", code);
            line.put("productName", m.path("name").asText());
            line.put("quantity", qty);
            line.put("originalSubtotal", orig * qty);
            line.put("subtotal", cur * qty);
        }

        ObjectNode data = mapper.createObjectNode();
        data.put("productOriginalPrice", productOriginal);
        data.put("productPrice", productPrice);
        data.put("deliveryOriginalPrice", 0L);
        data.put("deliveryPrice", 0L);
        data.put("packingOriginalPrice", 0L);
        data.put("packingPrice", 0L);
        data.put("tablewarePrice", 0L);
        data.put("originalPrice", productOriginal);
        data.put("discount", productOriginal - productPrice);
        data.put("price", productPrice);
        data.set("productList", list);

        ObjectNode sc = mapper.createObjectNode();
        sc.put("success", true);
        sc.put("code", 200);
        sc.put("message", "请求成功");
        sc.set("data", data);
        ObjectNode result = mapper.createObjectNode();
        result.set("structuredContent", sc);
        return result;
    }

    private static long yuan(JsonNode node, String key) {
        JsonNode v = node.get(key);
        if (v == null || v.isNull()) {
            return 0L;
        }
        try {
            return Money.parseYuanToCents(v.asText());
        } catch (Exception e) {
            return 0L;
        }
    }
}
