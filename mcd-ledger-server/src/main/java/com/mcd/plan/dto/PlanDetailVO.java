package com.mcd.plan.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 方案全量状态（GET /plans/{id} 与 /demo/plan 的 data）：成员 / 选餐 / 汇总。金额单位：分。 */
@Data
public class PlanDetailVO {
    private Long id;
    private String title;
    private String storeId;
    private String storeName;
    private String channel;
    private String mealTime;
    private String status;
    private List<PlanParticipantVO> participants = new ArrayList<>();
    private List<ParticipantSelectionVO> selections = new ArrayList<>();
    private PlanSummaryVO summary;
}
