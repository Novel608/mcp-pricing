package com.mcd.ledger.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 归属/规则变更审计（设计 §7 audit_log）。金额无关，记录操作留痕。 */
@Data
@TableName("audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ledgerId;

    private String action;

    private String target;

    @TableField("before_json")
    private String beforeJson;

    @TableField("after_json")
    private String afterJson;

    private String operator;

    private LocalDateTime createdAt;
}
