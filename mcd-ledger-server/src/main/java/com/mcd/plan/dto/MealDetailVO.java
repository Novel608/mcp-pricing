package com.mcd.plan.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 餐品/套餐详情 VO（query-meal-detail），含规格轮次与可选项。金额单位：分。 */
@Data
public class MealDetailVO {
    private String code;
    private String name;
    private Long priceCents;
    private List<RoundVO> rounds = new ArrayList<>();

    @Data
    public static class RoundVO {
        private String id;
        private String name;
        private Integer quantity;
        private List<ChoiceVO> choices = new ArrayList<>();
    }

    @Data
    public static class ChoiceVO {
        private String code;
        private String name;
        private Integer quantity;
        /** 差价（分，diffPrice 形如 "+ ¥0"/"¥3"） */
        private Long diffPriceCents;
        private Boolean isDefault;
        private Boolean supportModify;
    }
}
