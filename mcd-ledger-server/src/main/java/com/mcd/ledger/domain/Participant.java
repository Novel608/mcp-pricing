package com.mcd.ledger.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 参与人（设计 §7 participant）。人员与项目绑定。 */
@Data
@TableName("participant")
public class Participant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ledgerId;

    private String displayName;

    /** 人员引用（工号/花名） */
    private String personRef;

    private Long projectId;

    private Integer sortOrder;
}
