package com.mcd.ledger.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcd.ledger.dto.PersonRecon;
import com.mcd.ledger.dto.ProjectRecon;
import com.mcd.ledger.dto.ReconciliationReport;
import com.mcd.ledger.engine.model.ReconInput;
import com.mcd.ledger.engine.model.ReconInput.EngineAlloc;
import com.mcd.ledger.engine.model.ReconInput.EngineItem;
import com.mcd.ledger.engine.model.ReconInput.EngineOrder;
import com.mcd.ledger.engine.model.ReconInput.EngineParticipant;
import com.mcd.ledger.engine.model.ReconInput.EngineProject;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * QA 补测（严过关）：AllocationEngine 边界行为（寇豆码主链路之外的挑剔验证）。
 *  1. R1：优惠/配送费只在本订单参与人间分摊，不跨单泄漏；
 *  2. UNASSIGNED 餐品进 pendingItems，不进任何合计；
 *  3. projectId 为空/未知的参与人进入合成「未归集」项目桶，金额不静默丢失；
 *  4. 分摊指向非本账参与人（数据异常）时金额被丢弃且尾差暴露（不静默抹平）；
 *  5. includeDeliveryInSubsidy 开关与 limit=null 的餐补口径。
 */
class AllocationEngineEdgeTest {

    private final AllocationEngine engine = new AllocationEngine();

    private static EngineParticipant person(long id, String name, Long projectId) {
        return new EngineParticipant(id, name, projectId, (int) id);
    }

    private static EngineItem personal(long itemId, String name, long total, long pid) {
        return new EngineItem(itemId, name, total, AllocationType.PERSONAL,
                List.of(new EngineAlloc(pid, 1)));
    }

    private static PersonRecon personOf(ReconciliationReport r, long id) {
        return r.getPersons().stream().filter(p -> p.getParticipantId() == id).findFirst().orElseThrow();
    }

    private static ProjectRecon projectByName(ReconciliationReport r, String name) {
        return r.getProjects().stream().filter(p -> name.equals(p.getName())).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("R1：订单2 的优惠不分给只参与订单1 的人（单内权重，非全局权重）")
    void r1_discountStaysWithinOrder() {
        // 甲只在订单1（食品 1000，优惠 -100，实付 900）；乙只在订单2（食品 1000，优惠 -500，实付 500）
        List<EngineParticipant> participants = List.of(person(1L, "甲", 10L), person(2L, "乙", 10L));
        List<EngineProject> projects = List.of(new EngineProject(10L, "P", "P"));
        List<EngineOrder> orders = List.of(
                new EngineOrder(1L, "O1", 900L, -100L, 0L, List.of(personal(11L, "甲餐", 1000L, 1L))),
                new EngineOrder(2L, "O2", 500L, -500L, 0L, List.of(personal(12L, "乙餐", 1000L, 2L))));
        ReconciliationReport r = engine.reconcile(
                new ReconInput(null, false, orders, participants, projects));

        assertEquals(900L, personOf(r, 1L).getTotalCents(), "甲只承担订单1 的 -100 优惠");
        assertEquals(500L, personOf(r, 2L).getTotalCents(), "乙只承担订单2 的 -500 优惠");
        assertEquals(1400L, r.getOrdersTotalCents());
        assertEquals(1400L, r.getPersonsTotalCents());
        assertEquals(0L, r.getDiffCents());
        // 若误用全局权重（两人各 1000），甲会被多摊订单2 优惠 —— 上面断言即证伪
    }

    @Test
    @DisplayName("R1：同人两单时，每单优惠按该单内本人食品小计权重（而非两单合并权重）")
    void r1_samePersonTwoOrders_perOrderWeights() {
        // 订单1：甲食品 900、乙食品 100；优惠 -100 → 甲 -90、乙 -10
        // 订单2：甲食品 100、乙食品 900；优惠 -100 → 甲 -10、乙 -90
        List<EngineParticipant> participants = List.of(person(1L, "甲", 10L), person(2L, "乙", 10L));
        List<EngineProject> projects = List.of(new EngineProject(10L, "P", "P"));
        List<EngineOrder> orders = List.of(
                new EngineOrder(1L, "O1", 900L, -100L, 0L, List.of(
                        personal(11L, "x", 900L, 1L), personal(12L, "y", 100L, 2L))),
                new EngineOrder(2L, "O2", 900L, -100L, 0L, List.of(
                        personal(13L, "x", 100L, 1L), personal(14L, "y", 900L, 2L))));
        ReconciliationReport r = engine.reconcile(
                new ReconInput(null, false, orders, participants, projects));
        // 甲：900-90 + 100-10 = 900；乙：100-10 + 900-90 = 900
        assertEquals(900L, personOf(r, 1L).getTotalCents());
        assertEquals(900L, personOf(r, 2L).getTotalCents());
        assertEquals(1800L, r.getOrdersTotalCents());
        assertEquals(0L, r.getDiffCents());
    }

    @Test
    @DisplayName("UNASSIGNED 餐品：进 pendingItems，不计入任何合计，尾差暴露")
    void unassignedItem_pendingAndDiffExposed() {
        List<EngineParticipant> participants = List.of(person(1L, "甲", 10L));
        List<EngineProject> projects = List.of(new EngineProject(10L, "P", "P"));
        List<EngineOrder> orders = List.of(new EngineOrder(1L, "O1", 1500L, 0L, 0L, List.of(
                personal(11L, "已分配", 1000L, 1L),
                new EngineItem(12L, "未分配", 500L, AllocationType.UNASSIGNED, List.of()))));
        ReconciliationReport r = engine.reconcile(
                new ReconInput(null, false, orders, participants, projects));
        assertEquals(1, r.getPendingItems().size());
        assertEquals("未分配", r.getPendingItems().get(0).getName());
        assertEquals(1000L, personOf(r, 1L).getTotalCents());
        assertEquals(1500L, r.getOrdersTotalCents());
        assertEquals(1000L, r.getPersonsTotalCents());
        assertEquals(500L, r.getDiffCents(), "未分配金额必须体现在尾差，不被静默抹平");
        assertFalse(r.isBalanced());
    }

    @Test
    @DisplayName("projectId 为空的参与人：进入合成「未归集」桶，三方仍恒等")
    void nullProjectId_syntheticBucketKeepsIdentity() {
        List<EngineParticipant> participants = List.of(
                person(1L, "甲", 10L), person(2L, "无项目乙", null));
        List<EngineProject> projects = List.of(new EngineProject(10L, "P", "P"));
        List<EngineOrder> orders = List.of(new EngineOrder(1L, "O1", 2000L, 0L, 0L, List.of(
                personal(11L, "x", 1200L, 1L), personal(12L, "y", 800L, 2L))));
        ReconciliationReport r = engine.reconcile(
                new ReconInput(null, false, orders, participants, projects));

        ProjectRecon unassigned = projectByName(r, "未归集");
        assertNotNull(unassigned, "应存在合成「未归集」项目");
        assertNull(unassigned.getProjectId());
        assertEquals("UNASSIGNED", unassigned.getCode());
        assertEquals(800L, unassigned.getTotalCents(), "无项目参与人的金额必须进合成桶，不静默丢弃");
        assertEquals(2000L, r.getOrdersTotalCents());
        assertEquals(2000L, r.getPersonsTotalCents());
        assertEquals(2000L, r.getProjectsTotalCents());
        assertEquals(0L, r.getDiffCents());
        // 合成桶 + 正式项目 = 人员合计
        long sumProjects = r.getProjects().stream().mapToLong(ProjectRecon::getTotalCents).sum();
        assertEquals(r.getPersonsTotalCents(), sumProjects);
    }

    @Test
    @DisplayName("projectId 指向不存在项目：同样进合成桶（容错不丢钱）")
    void unknownProjectId_syntheticBucket() {
        List<EngineParticipant> participants = List.of(person(1L, "甲", 999L)); // 999 不存在
        List<EngineProject> projects = List.of(new EngineProject(10L, "P", "P"));
        List<EngineOrder> orders = List.of(new EngineOrder(1L, "O1", 1000L, 0L, 0L, List.of(
                personal(11L, "x", 1000L, 1L))));
        ReconciliationReport r = engine.reconcile(
                new ReconInput(null, false, orders, participants, projects));
        assertEquals(1000L, projectByName(r, "未归集").getTotalCents());
        assertEquals(0L, r.getDiffCents());
    }

    @Test
    @DisplayName("分摊指向非本账参与人：该份额被丢弃且尾差暴露（不静默抹平）")
    void allocationToStranger_droppedAndDiffExposed() {
        List<EngineParticipant> participants = List.of(person(1L, "甲", 10L));
        List<EngineProject> projects = List.of(new EngineProject(10L, "P", "P"));
        List<EngineOrder> orders = List.of(new EngineOrder(1L, "O1", 2000L, 0L, 0L, List.of(
                personal(11L, "x", 1000L, 1L), personal(12L, "y", 1000L, 42L /* 局外人 */))));
        ReconciliationReport r = engine.reconcile(
                new ReconInput(null, false, orders, participants, projects));
        assertEquals(1000L, personOf(r, 1L).getTotalCents());
        assertEquals(2000L, r.getOrdersTotalCents());
        assertEquals(1000L, r.getDiffCents(), "异常分摊必须让尾差现形，阻断导出");
        assertFalse(r.isBalanced());
    }

    @Test
    @DisplayName("餐补口径：含配送费开关只影响 subsidyBase，不影响 total 与恒等")
    void includeDeliveryToggle_affectsBaseOnly() {
        List<EngineParticipant> participants = List.of(person(1L, "甲", 10L));
        List<EngineProject> projects = List.of(new EngineProject(10L, "P", "P"));
        // 食品 1000，配送 +200，优惠 -100，实付 1100
        List<EngineOrder> orders = List.of(new EngineOrder(1L, "O1", 1100L, -100L, 200L, List.of(
                personal(11L, "x", 1000L, 1L))));
        ReconciliationReport without = engine.reconcile(
                new ReconInput(900L, false, orders, participants, projects));
        ReconciliationReport with = engine.reconcile(
                new ReconInput(900L, true, orders, participants, projects));

        assertEquals(1100L, personOf(without, 1L).getTotalCents());
        assertEquals(1100L, personOf(with, 1L).getTotalCents(), "total 不受开关影响");
        assertEquals(900L, personOf(without, 1L).getSubsidyBaseCents(), "不含配送费：1000-100");
        assertEquals(1100L, personOf(with, 1L).getSubsidyBaseCents(), "含配送费：1000-100+200");
        // base 1100 > limit 900 → within 900 / exceed 200；base 900 <= 900 → within 900 / exceed 200（total 恒 1100）
        assertEquals(900L, personOf(without, 1L).getWithinStandardCents());
        assertEquals(200L, personOf(without, 1L).getExceedStandardCents());
        assertEquals(900L, personOf(with, 1L).getWithinStandardCents());
        assertEquals(200L, personOf(with, 1L).getExceedStandardCents());
        assertEquals(0L, with.getDiffCents());
        assertEquals(0L, without.getDiffCents());
    }

    @Test
    @DisplayName("limit=null：不设上限，within = base，exceed = total − base")
    void nullLimit_noCap() {
        List<EngineParticipant> participants = List.of(person(1L, "甲", 10L));
        List<EngineProject> projects = List.of(new EngineProject(10L, "P", "P"));
        List<EngineOrder> orders = List.of(new EngineOrder(1L, "O1", 1200L, 0L, 200L, List.of(
                personal(11L, "x", 1000L, 1L))));
        ReconciliationReport r = engine.reconcile(
                new ReconInput(null, false, orders, participants, projects));
        PersonRecon p = personOf(r, 1L);
        assertEquals(1000L, p.getSubsidyBaseCents());
        assertEquals(1000L, p.getWithinStandardCents());
        assertEquals(200L, p.getExceedStandardCents());
        assertTrue(p.getWithinStandardCents() + p.getExceedStandardCents() == p.getTotalCents());
    }
}
