package com.mcd.ledger.dto;

import lombok.Data;

/** 待确认餐品（UNASSIGNED，不计入合计；API 契约 reconciliation.pendingItems[]）。 */
@Data
public class PendingItem {
    private Long orderId;
    private Long itemId;
    private String name;
    private Long itemTotalCents;
}
