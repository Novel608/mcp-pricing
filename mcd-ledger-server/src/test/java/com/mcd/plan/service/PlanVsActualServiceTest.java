package com.mcd.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcd.ledger.common.BizException;
import com.mcd.ledger.domain.MealLedger;
import com.mcd.ledger.mapper.MealLedgerMapper;
import com.mcd.plan.dto.CreatePlanRequest;
import com.mcd.plan.dto.PlanVsActualVO;
import com.mcd.plan.engine.PlanPricingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 转出与差异对照（设计 §3.4 #11/#12）：符号约定 diff = 实际 − 预期；未关联 / 无基线的两条错误分支。
 */
@SpringBootTest(classes = com.mcd.ledger.LedgerApplication.class,
        properties = "spring.datasource.url=jdbc:h2:mem:vsplantest;MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE")
class PlanVsActualServiceTest {

    @Autowired
    private PlanToLedgerConverter converter;
    @Autowired
    private PlanVsActualService vsActual;
    @Autowired
    private PlanPricingService pricing;
    @Autowired
    private MealPlanService planService;
    @Autowired
    private MealLedgerMapper ledgerMapper;
    @Autowired
    private com.mcd.ledger.service.LedgerService ledgerService;

    @Test
    void convert_writesSourcePlanIdAndBaseline() {
        pricing.price(1L, "1", "1");
        Long ledgerId = converter.convert(1L);

        MealLedger ledger = ledgerMapper.selectById(ledgerId);
        assertEquals(1L, ledger.getSourcePlanId());
        assertEquals(19650L, ledger.getExpectedTotalCents());
        assertEquals("CONVERTED", planService.getPlan(1L).getStatus());
        // 人员 1:1 迁移 + 项目按方案侧绑定预填（6 人 / 2 项目）
        assertEquals(6, ledgerService.getLedger(ledgerId).getParticipants().size());
        assertEquals(2, ledgerService.getLedger(ledgerId).getProjects().size());
    }

    @Test
    void vsPlan_noOrdersYet_differenceIsNegativeUnder() {
        pricing.price(1L, "1", "1");
        Long ledgerId = converter.convert(1L);

        PlanVsActualVO vo = vsActual.vsPlan(ledgerId);
        assertEquals(1L, vo.getPlanId());
        assertEquals(19650L, vo.getExpectedTotalCents());
        assertEquals(0L, vo.getActualTotalCents(), "新账尚未挂订单 → 实际 0");
        assertEquals(-19650L, vo.getDiffCents());
        assertEquals("UNDER", vo.getDirection());
        assertEquals(6, vo.getPersons().size());
        assertTrue(vo.getBalanced(), "人员级差异之和必须等于总额差异");
        assertNotNull(vo.getBaselineConfidence());
    }

    @Test
    void vsPlan_ledgerNotFromPlan_throwsPlanNotLinked() {
        // 手工新建一本与选餐无关的账 → 无 source_plan_id
        com.mcd.ledger.dto.CreateLedgerRequest req = new com.mcd.ledger.dto.CreateLedgerRequest();
        req.setTitle("手工建账");
        Long ledgerId = ledgerService.createInternal(req);
        BizException ex = assertThrows(BizException.class, () -> vsActual.vsPlan(ledgerId));
        assertEquals("PLAN_NOT_LINKED", ex.getErrorCode().code());
    }

    /** 沙盒账 id=1 播种时已带选餐预算基线 → 可直接对照（预期 19650 vs 实际 22800） */
    @Test
    void vsPlan_seededLedger_overBudget3150() {
        PlanVsActualVO vo = vsActual.vsPlan(1L);
        assertEquals(1L, vo.getPlanId());
        assertEquals(19650L, vo.getExpectedTotalCents());
        assertEquals(22800L, vo.getActualTotalCents());
        assertEquals(3150L, vo.getDiffCents());
        assertEquals("OVER", vo.getDirection());
        assertEquals(6, vo.getPersons().size());
        assertTrue(vo.getBalanced());
    }

    @Test
    void vsPlan_convertedWithoutPricing_throwsBaselineUnavailable() {
        CreatePlanRequest req = new CreatePlanRequest();
        req.setTitle("未核价方案");
        req.setStoreId("190001");
        req.setChannel("麦乐送");
        CreatePlanRequest.ParticipantSpec p = new CreatePlanRequest.ParticipantSpec();
        p.setDisplayName("甲");
        req.getParticipants().add(p);
        Long planId = planService.createPlan(req);

        Long ledgerId = converter.convert(planId);
        MealLedger ledger = ledgerMapper.selectById(ledgerId);
        assertEquals(planId, ledger.getSourcePlanId());
        assertNull(ledger.getExpectedTotalCents(), "无官方核价基线 → 预期留空");

        BizException ex = assertThrows(BizException.class, () -> vsActual.vsPlan(ledgerId));
        assertEquals("PLAN_BASELINE_UNAVAILABLE", ex.getErrorCode().code());
    }

    @Test
    void vsPlan_unknownLedger_throwsLedgerNotFound() {
        BizException ex = assertThrows(BizException.class, () -> vsActual.vsPlan(9999L));
        assertEquals("LEDGER_NOT_FOUND", ex.getErrorCode().code());
    }

    @Test
    void personDiffSum_matchesTotalDiff() {
        pricing.price(1L, "1", "1");
        PlanVsActualVO vo = vsActual.vsPlan(converter.convert(1L));
        assertEquals(vo.getDiffCents(), vsActual.personDiffSum(vo.getPersons()));
        assertFalse(vo.getPersons().isEmpty());
    }
}
