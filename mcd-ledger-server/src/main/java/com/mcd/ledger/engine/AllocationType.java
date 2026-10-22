package com.mcd.ledger.engine;

/** 餐品归属类型（设计 §4.2）：整份归人 / 多人共享 / 未分配。 */
public enum AllocationType {
    /** 整份归一个人 */
    PERSONAL,
    /** 多人按权重共享 */
    SHARED,
    /** 未分配，进待确认区，不计入合计 */
    UNASSIGNED
}
