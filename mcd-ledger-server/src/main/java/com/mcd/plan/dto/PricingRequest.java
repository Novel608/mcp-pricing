package com.mcd.plan.dto;

import lombok.Data;

/** 官方核价请求（设计 §3.4 #9）：orderType / beType 缺省按官方示例取 1。 */
@Data
public class PricingRequest {

    /** 订单类型，官方示例 "1" */
    private String orderType;

    /** 业务类型，官方示例 "1" */
    private String beType;
}
