package com.mcd.ledger.dto;

import lombok.Data;

/** 餐品分摊行（API 契约 items[].allocations[]）。 */
@Data
public class AllocVO {
    private Long participantId;
    private Integer weight;
    private Long allocatedCents;
}
