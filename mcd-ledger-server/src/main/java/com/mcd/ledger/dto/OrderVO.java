package com.mcd.ledger.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 订单快照（API 契约 orders[]）。金额单位：分。 */
@Data
public class OrderVO {
    private Long id;
    private String mcpOrderId;
    private String channel;
    private String orderStatus;
    private String dataConfidence;
    private Long paidTotalCents;
    private Long discountCents;
    private Long deliveryFeeCents;
    private List<ItemVO> items = new ArrayList<>();
}
