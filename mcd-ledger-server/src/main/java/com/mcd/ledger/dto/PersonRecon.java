package com.mcd.ledger.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 人员核对行（API 契约 reconciliation.persons[]）。可展开 formulaLines。 */
@Data
public class PersonRecon {
    private Long participantId;
    private String displayName;
    private Long projectId;
    private Long totalCents;
    private Long subsidyBaseCents;
    private Long withinStandardCents;
    private Long exceedStandardCents;
    private List<String> formulaLines = new ArrayList<>();
}
