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

    private LocalDateTime createdAt;
}
