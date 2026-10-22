package com.mcd.ledger.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 餐账全量聚合（API 契约 GET /ledgers/{id} 与 /demo/ledger 的 data）。 */
@Data
public class LedgerDetailVO {
    private Long id;
    private String title;
    private String mealTime;
    private String operatorName;
    private Long subsidyLimitCents;
    private Boolean includeDeliveryInSubsidy;
    private String status;
    private List<OrderVO> orders = new ArrayList<>();
    private List<ParticipantVO> participants = new ArrayList<>();
    private List<ProjectVO> projects = new ArrayList<>();
    private ReconciliationReport reconciliation;
}
