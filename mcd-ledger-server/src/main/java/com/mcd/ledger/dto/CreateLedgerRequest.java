package com.mcd.ledger.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 创建餐账请求（含初始参与人与项目）。 */
@Data
public class CreateLedgerRequest {

    @NotBlank(message = "标题不能为空")
    private String title;

    /** ISO-8601 本地时间，如 2026-10-22T20:00:00 */
    private String mealTime;

    private String operatorName;

    private Long subsidyLimitCents;

    private Boolean includeDeliveryInSubsidy;

    @Valid
    private List<ParticipantSpec> participants = new ArrayList<>();

    @Valid
    private List<ProjectSpec> projects = new ArrayList<>();

    @Data
    public static class ParticipantSpec {
        private String displayName;
        private String personRef;
        private Long projectId;
        private Integer sortOrder;
    }

    @Data
    public static class ProjectSpec {
        private String name;
        private String code;
    }
}
