package com.mcd.plan.dto;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 预期基线出参（设计 §3.4 #9/#10）：R5 恒等（Σ 每人 == expectedTotalCents）。 */
@Data
public class SnapshotVO {

    private Long id;
    private Long planId;
    private Long expectedSubtotalCents;
    private Long expectedDiscountCents;
    private Long expectedDeliveryFeeCents;
    private Long expectedTotalCents;
    private List<PerPersonExpected> perPerson = new ArrayList<>();
    private LocalDateTime pricedAt;
    private String confidence;
    private Boolean partial;
    private String itemsHash;
    private String menuCacheVersion;
    /** 软提示：选餐已变更或报价超出时效（不阻断） */
    private Boolean stale;
    private String staleReason;

    @Data
    public static class PerPersonExpected {
        private Long participantId;
        private String displayName;
        private Long expectedCents;
    }
}
