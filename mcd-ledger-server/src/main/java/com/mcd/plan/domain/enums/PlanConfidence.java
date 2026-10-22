package com.mcd.plan.domain.enums;

/**
 * 选餐域私有置信度（设计 §7.5：与对账域 DataConfidence 不合并、不互转）。
 * ESTIMATED_CURRENT / PENDING_PRICING
 */
public enum PlanConfidence {
    /** 依据当前菜单参考价估算 */
    ESTIMATED_CURRENT,
    /** 待官方核价 */
    PENDING_PRICING
}
