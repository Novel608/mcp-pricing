package com.mcd.ledger.mcp.dto;

import lombok.Data;

/** 脱敏可选订单卡片（order-list 摘要，供前端勾选）。金额单位：分。 */
@Data
public class OrderCard {
    private String mcpOrderId;
    private String channel;
    private String orderStatus;
    private Long paidTotalCents;
    private String orderTime;
}
