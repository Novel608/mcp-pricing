package com.mcd.ledger.mcp;

/**
 * 三态数据置信度（设计 §3.3，仅对账域）。
 * VERIFIED_HISTORICAL / AGREED_ALLOCATION / PENDING_CONFIRMATION
 */
public enum DataConfidence {
    /** 历史商品金额、优惠、配送费、实付总额齐全且自洽 */
    VERIFIED_HISTORICAL,
    /** 仅实付总额可靠，子项单价缺失或套餐无拆解 */
    AGREED_ALLOCATION,
    /** 金额缺失、口径不清、取消/退款待核实 */
    PENDING_CONFIRMATION
}
