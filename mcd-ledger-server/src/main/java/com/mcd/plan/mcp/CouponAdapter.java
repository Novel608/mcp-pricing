package com.mcd.plan.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcd.ledger.mcp.McpClient;
import com.mcd.ledger.mcp.McpEnvelope;
import com.mcd.ledger.mcp.McpToolWhitelist;
import com.mcd.plan.dto.CouponVO;
import java.util.ArrayList;
import java.util.List;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * 优惠券适配层（query-store-coupons，R3 券匹配数据源）。
 * data 直接为券数组；缓存 5min（设计 §7.4）。只展示不领券。置于 plan 包（plan→ledger）。
 */
@Component
public class CouponAdapter {

    private final McpToolWhitelist whitelist;
    private final McpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public CouponAdapter(McpToolWhitelist whitelist, McpClient client) {
        this.whitelist = whitelist;
        this.client = client;
    }

    @Cacheable(cacheNames = "coupons", key = "#storeId")
    public List<CouponVO> listStoreCoupons(String storeId, String orderType, String beType) {
        whitelist.assertReadable("query-store-coupons");
        ObjectNode args = mapper.createObjectNode();
        args.put("storeCode", storeId);
        args.put("orderType", orderType == null ? "1" : orderType);
        args.put("beType", beType == null ? "1" : beType);
        JsonNode data = McpEnvelope.unwrap(client.call("query-store-coupons", args));
        List<CouponVO> out = new ArrayList<>();
        if (data != null && data.isArray()) {
            for (JsonNode c : data) {
                CouponVO vo = new CouponVO();
                vo.setTitle(text(c, "title"));
                vo.setCouponId(text(c, "couponId"));
                vo.setCouponCode(text(c, "couponCode"));
                vo.setTradeDateTime(text(c, "tradeDateTime"));
                vo.setPromotionId(text(c, "promotionId"));
                JsonNode products = c.get("products");
                if (products != null && products.isArray()) {
                    for (JsonNode p : products) {
                        CouponVO.ProductVO pv = new CouponVO.ProductVO();
                        pv.setProductCode(text(p, "productCode"));
                        pv.setProductName(text(p, "productName"));
                        vo.getProducts().add(pv);
                    }
                }
                out.add(vo);
            }
        }
        return out;
    }

    private static String text(JsonNode node, String key) {
        JsonNode v = node.get(key);
        return (v != null && !v.isNull()) ? v.asText() : null;
    }
}
