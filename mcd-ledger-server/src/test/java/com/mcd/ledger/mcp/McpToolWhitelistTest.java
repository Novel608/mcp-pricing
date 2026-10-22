package com.mcd.ledger.mcp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.mcd.ledger.common.AuditLogger;
import com.mcd.ledger.common.BizException;
import com.mcd.ledger.common.ErrorCode;
import org.junit.jupiter.api.Test;

/** 白名单默认拒绝制：仅 7 个只读工具放行，其余（尤其写工具）拒绝并审计。 */
class McpToolWhitelistTest {

    private final AuditLogger auditLogger = mock(AuditLogger.class);
    private final McpToolWhitelist whitelist = new McpToolWhitelist(auditLogger);

    @Test
    void readableTools_pass() {
        for (String tool : new String[]{
                "order-list", "query-order",
                "query-meals", "query-meal-detail", "calculate-price",
                "query-store-coupons", "query-nearby-stores"}) {
            assertTrue(whitelist.isReadable(tool), tool);
            assertDoesNotThrow(() -> whitelist.assertReadable(tool), tool);
        }
    }

    @Test
    void writeTools_deniedWithAudit() {
        for (String tool : new String[]{
                "create-order", "cancel-order", "draw-lottery",
                "auto-bind-coupons", "mall-order", "unknown-tool"}) {
            assertFalse(whitelist.isReadable(tool), tool);
            BizException ex = assertThrows(BizException.class,
                    () -> whitelist.assertReadable(tool), tool);
            assertEquals(ErrorCode.MCP_WRITE_TOOL_DENIED, ex.getErrorCode());
            verify(auditLogger).toolDenied(tool);
        }
    }
}
