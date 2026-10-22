package com.mcd.ledger.mcp;

import com.mcd.ledger.common.AuditLogger;
import com.mcd.ledger.common.BizException;
import com.mcd.ledger.common.ErrorCode;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * MCP 工具白名单（设计 P4 / §4.3 T-5）：**默认拒绝**制。
 * 仅 order-list + query-order 两个读工具放行；命中非白名单即抛
 * BizException(MCP_WRITE_TOOL_DENIED) 并写审计日志，不发出任何网络请求。
 *
 * 零写操作：本类从根上杜绝误下单、误扣积分等写工具。
 */
@Component
public class McpToolWhitelist {

    /**
     * 放行的只读工具白名单：
     *   对账域：order-list, query-order
     *   选餐域：query-meals, query-meal-detail, calculate-price, query-store-coupons, query-nearby-stores
     * 其余（create-order / cancel-order / draw-lottery / auto-bind-coupons / mall-* 等写工具）默认拒绝。
     */
    public static final Set<String> READABLE = Set.of(
            "order-list", "query-order",
            "query-meals", "query-meal-detail", "calculate-price",
            "query-store-coupons", "query-nearby-stores");

    private final AuditLogger auditLogger;

    public McpToolWhitelist(AuditLogger auditLogger) {
        this.auditLogger = auditLogger;
    }

    public boolean isReadable(String tool) {
        return tool != null && READABLE.contains(tool);
    }

    /** 命中白名单则放行；否则写审计并抛 MCP_WRITE_TOOL_DENIED（403）。 */
    public void assertReadable(String tool) {
        if (!isReadable(tool)) {
            auditLogger.toolDenied(tool);
            throw BizException.of(ErrorCode.MCP_WRITE_TOOL_DENIED,
                    "本产品仅使用只读订单工具，该操作已被拒绝：" + tool);
        }
    }
}
