package com.mcd.plan.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 不可变预算基线（设计 §3.5 plan_snapshot，R5 预期恒等）。金额单位：分。 */
@Data
@TableName("plan_snapshot")
public class PlanSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;

    private Long expectedSubtotalCents;

    private Long expectedDiscountCents;

    private Long expectedDeliveryFeeCents;

    private Long expectedTotalCents;

    /** 每人预期 JSON（R5：Σ == expectedTotalCents） */
    private String perPersonExpectedJson;

    private LocalDateTime pricedAt;

    private String menuCacheVersion;

    private String itemsHash;

    /** ESTIMATED_CURRENT / PENDING_PRICING */
    private String confidence;

    /** 分项不可得标记（L0 内部字段级降级） */
    private Boolean partial;

    private LocalDateTime createdAt;
}
