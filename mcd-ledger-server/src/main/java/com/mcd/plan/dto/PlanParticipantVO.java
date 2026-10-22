package com.mcd.plan.dto;

import lombok.Data;

/** 选餐成员 VO。 */
@Data
public class PlanParticipantVO {
    private Long id;
    private String displayName;
    private String personRef;
    private Long projectId;
    private Integer sortOrder;
}
