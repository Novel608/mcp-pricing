package com.mcd.ledger.dto;

import lombok.Data;

/** 修改餐补规则请求。 */
@Data
public class SetSubsidyRequest {
    private Long limitCents;
    private Boolean includeDeliveryInSubsidy;
}
