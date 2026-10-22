package com.mcd.plan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 创建选餐方案请求（标题 / 门店 / 渠道 / 餐次时间 / 初始成员）。 */
@Data
public class CreatePlanRequest {

    @NotBlank(message = "标题不能为空")
    private String title;

    private String storeId;

    private String storeName;

    private String channel;

    /** ISO-8601 本地时间 */
    private String mealTime;

    @Valid
    private List<ParticipantSpec> participants = new ArrayList<>();

    @Data
    public static class ParticipantSpec {
        private String displayName;
        private String personRef;
        private Long projectId;
        private Integer sortOrder;
    }
}
