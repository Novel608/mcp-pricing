package com.mcd.plan.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 菜单餐品 VO（query-meals）。金额单位：分（元字符串经 parseYuanToCents 转换）。 */
@Data
public class MealVO {
    private String code;
    private String name;
    private Long currentPriceCents;
    private Long originalPriceCents;
    private String image;
    private String discountType;
    private Boolean canWithOrder;
    private List<String> tags = new ArrayList<>();
}
