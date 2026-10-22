package com.mcd.ledger.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 餐品项快照（设计 §7 order_item）。金额单位：分。 */
@Data
@TableName("order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ledgerOrderId;

    private String externalItemId;

    private String name;

    private Integer quantity;

    /** 餐品总价（分） */
    private Long itemTotalCents;

    /** 归属：PERSONAL / SHARED / UNASSIGNED */
    private String allocationType;

    /** 置信度（DataConfidence 名） */
    private String confidence;
}
