package com.mcd.ledger.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 导出快照（设计 §7 ledger_export_snapshot）。保证报销凭证可复现。 */
@Data
@TableName("ledger_export_snapshot")
public class LedgerExportSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ledgerId;

    private Integer version;

    private String payloadJson;

    private String totalsJson;

    private LocalDateTime createdAt;
}
