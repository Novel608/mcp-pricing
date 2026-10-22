package com.mcd.plan.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 组队选餐方案聚合根（设计 §3.5 meal_plan）。 */
@Data
@TableName("meal_plan")
public class MealPlan {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String title;

    private String storeId;

    private String storeName;

    /** 渠道：麦乐送 / 到店自提 */
    private String channel;

    private LocalDateTime mealTime;

    /** DRAFT / PRICED / CONVERTED */
    private String status;

    private LocalDateTime createdAt;
}
