package com.mcd.plan.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 预算 vs 实际差异（设计 §3.4 #12）：只读派生，<b>不落库</b>、不进 ReconciliationEngine。
 *
 * <p>符号约定：<b>diff = 实际 − 预期</b>（正 = 超支，负 = 结余），与对账域 diffCents（订单 − 人员）无关。
 */
@Data
public class PlanVsActualVO {

    private Long ledgerId;
    private Long planId;
    private Long expectedTotalCents;
    private Long actualTotalCents;
    /** 实际 − 预期 */
    private Long diffCents;
    /** OVER（超支） / UNDER（结余） / EQUAL（持平） */
    private String direction;
    /** 人员级差异之和是否与总额差异一致（R5 同源恒等） */
    private Boolean balanced;
    private String baselineConfidence;
    private Boolean baselineStale;
    private List<PersonDiff> persons = new ArrayList<>();

    @Data
    public static class PersonDiff {
        private Long participantId;
        private String displayName;
        private Long expectedCents;
        private Long actualCents;
        private Long diffCents;
    }
}
