package com.mcd.ledger.dto;

import lombok.Data;

/** 项目归集行（API 契约 reconciliation.projects[]）。 */
@Data
public class ProjectRecon {
    private Long projectId;
    private String name;
    private String code;
    private Long totalCents;
    private Long withinStandardCents;
    private Long exceedStandardCents;
}
