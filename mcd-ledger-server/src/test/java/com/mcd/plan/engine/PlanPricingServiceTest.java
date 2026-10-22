package com.mcd.plan.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcd.ledger.common.BizException;
import com.mcd.ledger.engine.PennyAllocator;
import com.mcd.plan.dto.CreatePlanRequest;
import com.mcd.plan.dto.PlanDetailVO;
import com.mcd.plan.dto.SnapshotVO;
import com.mcd.plan.dto.SubmitSelectionRequest;
import com.mcd.plan.service.MealPlanService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * R5 预期恒等测试：Σ 每人预期 == 官方总额（尾差必须为 0）。
 * 沙盒模式下 calculate-price 由 SandboxMcpClient 顶替，无需 Token。
 */
@SpringBootTest(classes = com.mcd.ledger.LedgerApplication.class,
        properties = "spring.datasource.url=jdbc:h2:mem:pricingtest;MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE")
class PlanPricingServiceTest {

    @Autowired
    private PlanPricingService pricing;
    @Autowired
    private MealPlanService planService;

    @Test
    void allocator_neverLeavesTailDifference() {
        // 100 分按 [1,1,1] 摊 → 34/33/33，尾差 0（权重口径与事后对账完全一致）
        List<Long> alloc = PennyAllocator.allocate(100L, List.of(1, 1, 1));
        long sum = alloc.stream().mapToLong(Long::longValue).sum();
        assertEquals(100L, sum);
        assertEquals(34L, alloc.get(0));
        assertEquals(33L, alloc.get(1));
        assertEquals(33L, alloc.get(2));
    }

    @Test
    void price_sandboxPlan_perPersonSumEqualsOfficialTotal() {
        SnapshotVO vo = pricing.price(1L, "1", "1");
        assertNotNull(vo.getId());
        long sum = vo.getPerPerson().stream().mapToLong(SnapshotVO.PerPersonExpected::getExpectedCents).sum();
        // R5：三方恒等的「预期」侧
        assertEquals(vo.getExpectedTotalCents(), sum, "Σ 每人预期必须等于官方总额（尾差 0）");
        assertEquals(6, vo.getPerPerson().size());
        // 沙盒选餐官方价合计 19650（code/单价全部取自 query-meals 抓取值）
        assertEquals(19650L, vo.getExpectedTotalCents());
        assertEquals("ESTIMATED_CURRENT", vo.getConfidence());
        assertFalse(vo.getPartial(), "官方返回 productList → 非 partial");
    }

    @Test
    void latest_staleDetectedAfterSelectionChange() {
        Long planId = newPlanWithOneParticipant();
        submitSingle(planId, 1, 2250L);
        SnapshotVO first = pricing.price(planId, "1", "1");
        assertEquals(2250L, first.getExpectedTotalCents());

        SnapshotVO latest = pricing.latest(planId);
        assertEquals(first.getId(), latest.getId());
        assertFalse(latest.getStale(), "刚核价不应 stale");

        // 变更选餐后基线指纹不再匹配 → stale 软提示（不阻断）
        submitSingle(planId, 9, 2250L);
        SnapshotVO after = pricing.latest(planId);
        assertTrue(after.getStale());
        assertNotNull(after.getStaleReason());
    }

    @Test
    void latest_withoutBaseline_throwsPlanBaselineUnavailable() {
        assertThrows(BizException.class, () -> pricing.latest(999L));
    }

    private Long newPlanWithOneParticipant() {
        CreatePlanRequest req = new CreatePlanRequest();
        req.setTitle("恒等测试方案");
        req.setStoreId("190001");
        req.setChannel("麦乐送");
        CreatePlanRequest.ParticipantSpec p = new CreatePlanRequest.ParticipantSpec();
        p.setDisplayName("甲");
        req.getParticipants().add(p);
        return planService.createPlan(req);
    }

    private void submitSingle(Long planId, int qty, long unitCents) {
        PlanDetailVO vo = planService.getPlan(planId);
        Long participantId = vo.getParticipants().get(0).getId();
        SubmitSelectionRequest req = new SubmitSelectionRequest();
        req.setParticipantId(participantId);
        SubmitSelectionRequest.SelectionItem it = new SubmitSelectionRequest.SelectionItem();
        it.setExternalItemId("1440");
        it.setName("麦辣鸡腿汉堡");
        it.setQuantity(qty);
        it.setUnitPriceCents(unitCents);
        req.getItems().add(it);
        planService.submitSelection(planId, req);
    }
}
