package com.mcd.plan.domain.enums;

/**
 * 推荐价格来源（R6 三元组必填之一，V1.4 终审新增）。
 * MCP_CALCULATED：官方 calculate-price 核算；MCP_REFERENCE：官方菜单参考价。
 * 注意：不存在也不得实现 PRICE_SOURCE_LOCAL_MENU（L3 禁止）。
 */
public enum PriceSource {
    MCP_CALCULATED,
    MCP_REFERENCE
}
