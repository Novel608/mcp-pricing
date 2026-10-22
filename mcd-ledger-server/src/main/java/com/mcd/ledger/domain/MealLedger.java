package com.mcd.ledger.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 餐账主表（设计 §7 meal_ledger）。一个餐次一本账。 */
@Data
@TableName("meal_ledger")
public class MealLedger {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private LocalDateTime mealTime;

    private String operatorName;

    /** 餐补上限（分） */
    private Long subsidyLimitCents;

    /** 餐补是否含配送费 */
    private Boolean includeDeliveryInSubsidy;

    /** 状态：DRAFT / CONFIRMED 等 */
    private String status;

    /**
     * 来源选餐方案（可空外键，设计 DR-2）。
     * 由 plan 域 {@code PlanToLedgerConverter} 写入；<b>ledger 域既有逻辑永远不读它</b>。
     */
    private Long sourcePlanId;

    /** 转出时官方核价的预期总额（分，可空；无基线则为 null） */
    private Long expectedTotalCents;

    private LocalDateTime createdAt;
}
