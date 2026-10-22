package com.mcd.plan.dto;

import lombok.Data;

/** 选餐项 VO。金额单位：分。 */
@Data
public class SelectionItemVO {
    private String externalItemId;
    private String name;
    private Integer quantity;
    private Long unitPriceCents;
    private String specOptionsJson;
}
