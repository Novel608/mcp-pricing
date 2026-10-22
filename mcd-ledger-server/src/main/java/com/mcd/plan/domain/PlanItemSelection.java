package com.mcd.plan.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 单条选餐项（设计 §3.5 plan_item_selection）。金额单位：分。 */
@Data
@TableName("plan_item_selection")
public class PlanItemSelection {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;

    private Long participantId;

    private String externalItemId;

    private String name;

    private Integer quantity;

    /** 单价（分，来自菜单参考价） */
    private Long unitPriceCents;

    /** 规格选项 JSON（rounds/choices） */
    private String specOptionsJson;
}
