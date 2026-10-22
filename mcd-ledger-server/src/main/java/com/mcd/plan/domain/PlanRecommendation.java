package com.mcd.plan.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 推荐留痕（设计 §3.5 plan_recommendation，R6 契约）。
 * COMBO/THRESHOLD：discountCents/addedCostCents/netSavingCents 必填且 netSaving=discount-addedCost；
 * COUPON：netSavingCents 为 NULL（券面额官方但适用性不可验证）。
 */
@Data
@TableName("plan_recommendation")
public class PlanRecommendation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;

    /** COMBO / THRESHOLD / COUPON */
    private String type;

    private String title;

    private String reasonJson;

    private Long discountCents;

    private Long addedCostCents;

    /** COUPON 类型为 NULL */
    private Long netSavingCents;

    /** MCP_CALCULATED / MCP_REFERENCE（R6 三元组必填） */
    private String priceSource;

    private Boolean accepted;

    private LocalDateTime createdAt;
}
