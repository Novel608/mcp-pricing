package com.mcd.ledger.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcd.ledger.common.BizException;
import com.mcd.ledger.common.ErrorCode;
import com.mcd.ledger.dto.PersonRecon;
import com.mcd.ledger.dto.ProjectRecon;
import com.mcd.ledger.dto.ReconciliationReport;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 三方恒等校验单测（沙盒 §9）：ordersTotal==personsTotal==projectsTotal==22800，diff=0；尾差非 0 阻断导出。 */
class ReconciliationEngineTest {

    private final AllocationEngine allocationEngine = new AllocationEngine();
    private final ReconciliationEngine reconciliationEngine = new ReconciliationEngine();

    @Test
    void sandbox_threeWayIdentity_22800() {
        ReconciliationReport r = allocationEngine.reconcile(SandboxFixtures.build());
        assertEquals(22800L, r.getOrdersTotalCents());
        assertEquals(22800L, r.getPersonsTotalCents());
        assertEquals(22800L, r.getProjectsTotalCents());
        assertEquals(0L, r.getDiffCents());
        // 三方逐一相等
        assertEquals(r.getOrdersTotalCents(), r.getPersonsTotalCents());
        assertEquals(r.getPersonsTotalCents(), r.getProjectsTotalCents());
    }

    @Test
    void sandbox_exportAllowed_whenBalanced() {
        ReconciliationReport r = allocationEngine.reconcile(SandboxFixtures.build());
        // 不抛异常即通过
        reconciliationEngine.assertExportable(r);
        assertTrue(r.isBalanced());
    }

    @Test
    void assemble_computesDiffFromOrdersMinusPersons() {
        ProjectRecon p = new ProjectRecon();
        p.setProjectId(1L);
        p.setTotalCents(100L);
        ReconciliationReport r = reconciliationEngine.assemble(150, 100, List.of(p), List.of(), List.of());
        assertEquals(150L, r.getOrdersTotalCents());
        assertEquals(100L, r.getPersonsTotalCents());
        assertEquals(100L, r.getProjectsTotalCents());
        assertEquals(50L, r.getDiffCents());
    }

    @Test
    void exportBlocked_whenDiffNotZero() {
        ReconciliationReport r = new ReconciliationReport();
        r.setOrdersTotalCents(22800L);
        r.setPersonsTotalCents(22300L);
        r.setProjectsTotalCents(22300L);
        r.setDiffCents(500L);
        BizException ex = assertThrows(BizException.class, () -> reconciliationEngine.assertExportable(r));
        assertEquals(ErrorCode.LEDGER_DIFF_NOT_ZERO, ex.getErrorCode());
    }
}
