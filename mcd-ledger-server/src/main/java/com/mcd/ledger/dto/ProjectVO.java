package com.mcd.ledger.dto;

import lombok.Data;

/** 项目（API 契约 projects[]）。 */
@Data
public class ProjectVO {
    private Long id;
    private String name;
    private String code;
}
