package com.mcd.ledger.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 订单快照（设计 §7 meal_ledger_order）。MCP query-order 返回并入库的不可变快照。金额单位：分。 */
@Data
@TableName("meal_ledger_order")
public class LedgerOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ledgerId;

    /** MCP 订单号；与 ledgerId 构成唯一约束，保证幂等 */
    private String mcpOrderId;

    /** 渠道：麦乐送 / 到店自提 */
    private String channel;

    /** 订单状态 */
    private String orderStatus;

    /** 数据置信度（DataConfidence 名） */
    private String dataConfidence;

    /** 实付总额（分） */
    private Long paidTotalCents;

    /** 优惠额（分，负数） */
    private Long discountCents;

    /** 配送费（分，正数） */
    private Long deliveryFeeCents;

    private LocalDateTime createdAt;
}
