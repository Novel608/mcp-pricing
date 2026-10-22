package com.mcd.ledger.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcd.ledger.dto.PersonRecon;
import com.mcd.ledger.dto.ProjectRecon;
import com.mcd.ledger.dto.ReconciliationReport;
import org.junit.jupiter.api.Test;

/**
 * 事后对账四级流水线单测（沙盒 §9 固定用例）。
 * 断言三方恒等 22800、各人合计、项目归集、餐补标准内/超标、尾差 0。
 */
class AllocationEngineTest {

    private final AllocationEngine engine = new AllocationEngine();

    private ReconciliationReport report;

    private ReconciliationReport report() {
        if (report == null) {
            report = engine.reconcile(SandboxFixtures.build());
        }
        return report;
    }

    private PersonRecon person(long id) {
        return report().getPersons().stream()
                .filter(p -> p.getParticipantId() == id)
                .findFirst().orElseThrow();
    }

    private ProjectRecon project(long id) {
        return report().getProjects().stream()
                .filter(p -> p.getProjectId() != null && p.getProjectId() == id)
                .findFirst().orElseThrow();
    }

    @Test
    void threeWayIdentity_22800_diffZero() {
        ReconciliationReport r = report();
        assertEquals(22800L, r.getOrdersTotalCents());
        assertEquals(22800L, r.getPersonsTotalCents());
        assertEquals(22800L, r.getProjectsTotalCents());
        assertEquals(0L, r.getDiffCents());
        assertTrue(r.isBalanced());
    }

    @Test
    void perPersonTotals_matchSandbox() {
        assertEquals(4316L, person(SandboxFixtures.LIN).getTotalCents());
        assertEquals(4316L, person(SandboxFixtures.CHEN).getTotalCents());
        assertEquals(3878L, person(SandboxFixtures.WANG).getTotalCents());
        assertEquals(3878L, person(SandboxFixtures.ZHAO).getTotalCents());
        assertEquals(3206L, person(SandboxFixtures.SUN).getTotalCents());
        assertEquals(3206L, person(SandboxFixtures.ZHOU).getTotalCents());
    }

    @Test
    void subsidyBase_matchSandbox() {
        assertEquals(4078L, person(SandboxFixtures.LIN).getSubsidyBaseCents());
        assertEquals(4078L, person(SandboxFixtures.CHEN).getSubsidyBaseCents());
        assertEquals(3666L, person(SandboxFixtures.WANG).getSubsidyBaseCents());
        assertEquals(3666L, person(SandboxFixtures.ZHAO).getSubsidyBaseCents());
        assertEquals(3206L, person(SandboxFixtures.SUN).getSubsidyBaseCents());
        assertEquals(3206L, person(SandboxFixtures.ZHOU).getSubsidyBaseCents());
    }

    @Test
    void withinAndExceed_matchSandbox() {
        assertEquals(3500L, person(SandboxFixtures.LIN).getWithinStandardCents());
        assertEquals(816L, person(SandboxFixtures.LIN).getExceedStandardCents());
        assertEquals(3500L, person(SandboxFixtures.WANG).getWithinStandardCents());
        assertEquals(378L, person(SandboxFixtures.WANG).getExceedStandardCents());
        assertEquals(3206L, person(SandboxFixtures.SUN).getWithinStandardCents());
        assertEquals(0L, person(SandboxFixtures.SUN).getExceedStandardCents());
        assertEquals(0L, person(SandboxFixtures.ZHOU).getExceedStandardCents());
    }

    @Test
    void withinPlusExceed_equalsPersonTotal() {
        for (PersonRecon p : report().getPersons()) {
            assertEquals(p.getTotalCents(), p.getWithinStandardCents() + p.getExceedStandardCents(),
                    "person " + p.getDisplayName());
        }
    }

    @Test
    void withinSum_20412_exceedSum_2388() {
        long within = report().getPersons().stream().mapToLong(PersonRecon::getWithinStandardCents).sum();
        long exceed = report().getPersons().stream().mapToLong(PersonRecon::getExceedStandardCents).sum();
        assertEquals(20412L, within);
        assertEquals(2388L, exceed);
        assertEquals(22800L, within + exceed);
    }

    @Test
    void projectAggregation_matchSandbox() {
        ProjectRecon a = project(SandboxFixtures.PROJ_A);
        assertEquals(15716L, a.getTotalCents());
        assertEquals(13706L, a.getWithinStandardCents());
        assertEquals(2010L, a.getExceedStandardCents());

        ProjectRecon b = project(SandboxFixtures.PROJ_B);
        assertEquals(7084L, b.getTotalCents());
        assertEquals(6706L, b.getWithinStandardCents());
        assertEquals(378L, b.getExceedStandardCents());
    }

    @Test
    void noPendingItems_allAllocated() {
        assertTrue(report().getPendingItems().isEmpty());
    }

    @Test
    void formulaLines_presentAndExpandable() {
        PersonRecon lin = person(SandboxFixtures.LIN);
        assertFalse(lin.getFormulaLines().isEmpty());
        // 林工参与两单，应有 2 行订单公式 + 1 行合计
        assertEquals(3, lin.getFormulaLines().size());
        assertTrue(lin.getFormulaLines().stream().anyMatch(l -> l.contains("MCD-20261022-01")));
        assertTrue(lin.getFormulaLines().stream().anyMatch(l -> l.contains("MCD-20261022-02")));
        assertTrue(lin.getFormulaLines().stream().anyMatch(l -> l.contains("合计")));
    }

    @Test
    void unassignedItem_goesToPendingAndBreaksIdentity() {
        // 构造一笔 UNASSIGNED 餐品 → 进 pending，人员合计 < 订单合计，尾差 > 0
        var in = SandboxFixtures.build();
        var order1 = in.orders().get(0);
        var items = new java.util.ArrayList<>(order1.items());
        items.add(new com.mcd.ledger.engine.model.ReconInput.EngineItem(
                999L, "未分配薯格", 500L, AllocationType.UNASSIGNED, java.util.List.of()));
        var newOrder1 = new com.mcd.ledger.engine.model.ReconInput.EngineOrder(
                order1.orderId(), order1.mcpOrderId(), order1.paidTotalCents() + 500L,
                order1.discountCents(), order1.deliveryFeeCents(), items);
        var newIn = new com.mcd.ledger.engine.model.ReconInput(
                in.subsidyLimitCents(), in.includeDeliveryInSubsidy(),
                java.util.List.of(newOrder1, in.orders().get(1)), in.participants(), in.projects());
        ReconciliationReport r = engine.reconcile(newIn);
        assertEquals(1, r.getPendingItems().size());
        assertEquals(500L, r.getDiffCents());
        assertFalse(r.isBalanced());
    }
}
