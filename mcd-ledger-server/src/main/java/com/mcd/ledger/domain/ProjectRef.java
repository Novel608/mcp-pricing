package com.mcd.ledger.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 项目引用（设计 §7 project_ref）。 */
@Data
@TableName("project_ref")
public class ProjectRef {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ledgerId;

    private String name;

    private String code;
}
