package com.mcd.plan.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 按餐品汇总行（设计 §3.4 契约 PlanDetailVO.aggregates）：
 * 同一餐品跨成员合并，供推荐引擎与转出餐账消费。金额单位：分。
 */
@Data
public class ItemAggregateVO {

    /** 餐品编码 = MCP productCode */
    private String externalItemId;

    /** 餐品名称（取首次出现的名称） */
    private String name;

    /** 合计份数 */
    private int totalQuantity = 0;

    /** 点了该餐品的成员 id（按出现顺序去重） */
    private List<Long> participantIds = new ArrayList<>();

    /** 该餐品小计 = Σ(单价 × 份数) */
    private long subtotalCents = 0L;
}
