package com.mcd.plan.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 优惠券 VO（query-store-coupons / 我的券包）。 */
@Data
public class CouponVO {
    private String title;
    private String couponId;
    private String couponCode;
    private String tradeDateTime;
    private String promotionId;
    private List<ProductVO> products = new ArrayList<>();

    @Data
    public static class ProductVO {
        private String productCode;
        private String productName;
    }
}
