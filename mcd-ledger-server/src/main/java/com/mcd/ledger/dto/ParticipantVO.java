package com.mcd.ledger.dto;

import lombok.Data;

/** 参与人（API 契约 participants[]）。 */
@Data
public class ParticipantVO {
    private Long id;
    private String displayName;
    private String personRef;
    private Long projectId;
    private Integer sortOrder;
}
