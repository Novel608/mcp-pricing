package com.mcd.plan.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 方案汇总：每人小计 + 总计（T03 组队选餐汇总；试算/预期分摊在 T04）。金额单位：分。 */
@Data
public class PlanSummaryVO {
    private List<PersonSubtotal> perPerson = new ArrayList<>();
    private Long totalCents;

    @Data
    public static class PersonSubtotal {
        private Long participantId;
        private String displayName;
        private Long subtotalCents;
    }
}
