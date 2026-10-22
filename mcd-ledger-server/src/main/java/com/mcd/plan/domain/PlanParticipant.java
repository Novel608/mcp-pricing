package com.mcd.plan.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 选餐参与人（设计 §3.5 plan_participant）；转出时 1:1 映射为对账 Participant。 */
@Data
@TableName("plan_participant")
public class PlanParticipant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;

    private String displayName;

    private String personRef;

    private Long projectId;

    private Integer sortOrder;
}
