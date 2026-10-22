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

    /** 按餐品汇总（跨成员合并），供推荐引擎（T04）与转出餐账（T05）消费。 */
    private List<ItemAggregateVO> aggregates = new ArrayList<>();

    private PlanSummaryVO summary;

    /** 推荐列表（端点 #2 主读接口一并返回，设计 §3.4）。 */
    private List<RecommendationVO> recommendations = new ArrayList<>();

    /** 最新预期基线；未核价时为 null。 */
    private SnapshotVO snapshot;
}
