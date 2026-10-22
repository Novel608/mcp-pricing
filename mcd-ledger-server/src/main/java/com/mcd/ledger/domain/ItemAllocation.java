package com.mcd.ledger.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 餐品到人的分摊（设计 §7 order_item_allocation）。allocatedCents 由 PennyAllocator 计算落库。 */
@Data
@TableName("order_item_allocation")
public class ItemAllocation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderItemId;

    private Long participantId;

    private Integer weight;

    /** 分摊金额（分） */
    private Long allocatedCents;
}
