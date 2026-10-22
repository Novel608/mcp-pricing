package com.mcd.ledger.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 三方核对报表（设计 §4.2 ReconciliationReport；API 契约 reconciliation）。 */
@Data
public class ReconciliationReport {
    private Long ordersTotalCents;
    private Long personsTotalCents;
    private Long projectsTotalCents;
    private Long diffCents;
    private List<PersonRecon> persons = new ArrayList<>();
    private List<ProjectRecon> projects = new ArrayList<>();
    private List<PendingItem> pendingItems = new ArrayList<>();

    public boolean isBalanced() {
        return diffCents != null && diffCents == 0L;
    }
}
