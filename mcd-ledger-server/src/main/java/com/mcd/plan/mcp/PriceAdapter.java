package com.mcd.plan.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcd.ledger.common.BizException;
import com.mcd.ledger.common.ErrorCode;
import com.mcd.ledger.mcp.McpClient;
import com.mcd.ledger.mcp.McpEnvelope;
import com.mcd.ledger.mcp.McpToolWhitelist;
import com.mcd.plan.dto.PricingResult;
import org.springframework.stereotype.Component;

/**
 * 官方核价适配层（calculate-price，设计 §3.4 #9 / T04 试算段）。
 *
 * <p>单位铁律：calculate-price 返回的金额**本身就是整数分**，本类直接读取，
 * 严禁再过 Money.parseYuanToCents（A1 实测结论：仅 order-list/query-order/query-meals 才是元字符串）。
 *
 * <p>零写操作：calculate-price 为只读算价，且必须先过白名单。
 */
@Component
public class PriceAdapter {

    private final McpToolWhitelist whitelist;
    private final McpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public PriceAdapter(McpToolWhitelist whitelist, McpClient client) {
        this.whitelist = whitelist;
        this.client = client;
    }

    /** @param items 元素为 [productCode, quantity] 二元组 */
    public PricingResult calculatePrice(String storeCode, String orderType, String beType,
                                        java.util.List<String[]> items) {
        whitelist.assertReadable("calculate-price");
        ObjectNode args = mapper.createObjectNode();
        args.put("storeCode", storeCode);
        args.put("orderType", orderType == null ? "1" : orderType);
        args.put("beType", beType == null ? "1" : beType);
        ArrayNode arr = args.putArray("items");
        for (String[] it : items) {
            ObjectNode line = arr.addObject();
            line.put("productCode", it[0]);
            line.put("quantity", Integer.parseInt(it[1]));
        }

        JsonNode data = McpEnvelope.unwrap(client.call("calculate-price", args));
        if (data == null || data.isNull()) {
            throw BizException.of(ErrorCode.MCP_UPSTREAM_ERROR, "calculate-price 未返回数据");
        }

        PricingResult r = new PricingResult();
        r.setProductOriginalPriceCents(lng(data, "productOriginalPrice"));
        r.setProductPriceCents(lng(data, "productPrice"));
        r.setDeliveryOriginalPriceCents(lng(data, "deliveryOriginalPrice"));
        r.setDeliveryPriceCents(lng(data, "deliveryPrice"));
        r.setOriginalPriceCents(lng(data, "originalPrice"));
        r.setDiscountCents(lng(data, "discount"));
        r.setTotalCents(lng(data, "price"));
        r.setTablewarePriceCents(lng(data, "tablewarePrice"));

        JsonNode list = data.get("productList");
        if (list != null && list.isArray()) {
            for (JsonNode n : list) {
                PricingResult.Line line = new PricingResult.Line();
                line.setProductCode(text(n, "productCode"));
                line.setProductName(text(n, "productName"));
                line.setQuantity(n.path("quantity").asInt());
                line.setOriginalSubtotalCents(lng(n, "originalSubtotal"));
                line.setSubtotalCents(lng(n, "subtotal"));
                r.getProductList().add(line);
            }
        }
        // 分项缺失 → 字段级降级（L0 内部 partial 标记，不独立成档）
        r.setPartial(r.getProductList().isEmpty());
        return r;
    }

    private static long lng(JsonNode node, String key) {
        JsonNode v = node.get(key);
        return (v == null || v.isNull()) ? 0L : v.asLong();
    }

    private static String text(JsonNode node, String key) {
        JsonNode v = node.get(key);
        return (v == null || v.isNull()) ? null : v.asText();
    }
}
