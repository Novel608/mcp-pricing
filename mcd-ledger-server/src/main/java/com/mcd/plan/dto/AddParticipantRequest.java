package com.mcd.plan.dto;

import lombok.Data;

/** 添加选餐成员请求（含项目绑定）。 */
@Data
public class AddParticipantRequest {
    private String displayName;
    private String personRef;
    private Long projectId;
    private Integer sortOrder;
}
