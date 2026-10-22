package com.mcd.plan.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * calculate-price 试算结果（设计 §3.4 #9）。
 * 官方返回即为整数分，适配层**不再做元→分转换**（与订单/菜单侧不同，见 A1 实测结论）。
 */
@Data
public class PricingResult {

    private long productOriginalPriceCents;
    private long productPriceCents;
    private long deliveryOriginalPriceCents;
    private long deliveryPriceCents;
    private long originalPriceCents;
    private long discountCents;
    private long totalCents;
    private long tablewarePriceCents;

    /** 分项可得性：官方返回 productList 即视为非 partial */
    private boolean partial = false;

    private List<Line> productList = new ArrayList<>();

    @Data
    public static class Line {
        private String productCode;
        private String productName;
        private int quantity;
        private long originalSubtotalCents;
        private long subtotalCents;
    }
}
