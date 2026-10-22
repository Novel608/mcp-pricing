package com.mcd.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcd.ledger.LedgerApplication;
import com.mcd.plan.mcp.CouponAdapter;
import com.mcd.plan.mcp.MenuAdapter;
import com.mcd.plan.dto.AddParticipantRequest;
import com.mcd.plan.dto.CreatePlanRequest;
import com.mcd.plan.dto.ParticipantSelectionVO;
import com.mcd.plan.dto.PlanDetailVO;
import com.mcd.plan.dto.SubmitSelectionRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 选餐全栈集成测试：沙盒方案种子 + CRUD/幂等 + 沙盒菜单/券（SandboxMcpClient 顶替数据源，无需 Token）。
 * 独立 mem db，避免与对账测试相互干扰。
 */
@SpringBootTest(classes = LedgerApplication.class,
        properties = "spring.datasource.url=jdbc:h2:mem:plantest;MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE")
class PlanIntegrationTest {

    @Autowired
    private MealPlanService service;
    @Autowired
    private MenuAdapter menuAdapter;
    @Autowired
    private CouponAdapter couponAdapter;

    @Test
    void sandboxPlan_seeded_andSummary() {
        PlanDetailVO vo = service.getPlan(1L);
        assertNotNull(vo);
        assertEquals("周六加班餐 · 组队选餐方案", vo.getTitle());
        assertEquals(6, vo.getParticipants().size());
        // 种子选餐（code/单价全部取自官方 query-meals 抓取值）：
        // 林(2250+1350)=3600 陈3600 王(2350+950)=3300 赵(2550+950)=3500 孙(1400+2250)=3650 周(1500+500)=2000
        assertEquals(19650L, vo.getSummary().getTotalCents());
        ParticipantSelectionVO sun = vo.getSelections().stream()
                .filter(s -> "孙工".equals(s.getDisplayName())).findFirst().orElseThrow();
        assertEquals(3650L, sun.getSubtotalCents());
        assertEquals(2, sun.getItems().size());
        // 按餐品汇总：9 个餐品，其中「麦辣鸡腿汉堡」「薯条」「可乐」各 2 份（可合成 2 个三件套）
        assertEquals(9, vo.getAggregates().size());
        assertEquals(2, vo.getAggregates().stream()
                .filter(a -> "1440".equals(a.getExternalItemId())).findFirst().orElseThrow()
                .getTotalQuantity());
    }

    @Test
    void createPlan_addParticipant_submitSelection_idempotentOverwrite() {
        CreatePlanRequest req = new CreatePlanRequest();
        req.setTitle("测试方案");
        req.setStoreId("S1");
        req.setChannel("麦乐送");
        CreatePlanRequest.ParticipantSpec p = new CreatePlanRequest.ParticipantSpec();
        p.setDisplayName("小李");
        req.getParticipants().add(p);
        Long planId = service.createPlan(req);

        AddParticipantRequest add = new AddParticipantRequest();
        add.setDisplayName("小王");
        service.addParticipant(planId, add);

        // 提交选餐
        SubmitSelectionRequest sel = new SubmitSelectionRequest();
        sel.setParticipantId(planParticipantId(planId, "小李"));
        SubmitSelectionRequest.SelectionItem it = new SubmitSelectionRequest.SelectionItem();
        it.setExternalItemId("C1");
        it.setName("麦辣鸡腿堡");
        it.setQuantity(2);
        it.setUnitPriceCents(1600L);
        sel.getItems().add(it);
        service.submitSelection(planId, sel);

        PlanDetailVO vo = service.getPlan(planId);
        ParticipantSelectionVO xiaoli = vo.getSelections().stream()
                .filter(s -> "小李".equals(s.getDisplayName())).findFirst().orElseThrow();
        assertEquals(3200L, xiaoli.getSubtotalCents()); // 1600 × 2

        // 幂等覆盖：改为 1 份 → 小计应变 1600，且不累积
        sel.getItems().get(0).setQuantity(1);
        service.submitSelection(planId, sel);
        PlanDetailVO vo2 = service.getPlan(planId);
        ParticipantSelectionVO xiaoli2 = vo2.getSelections().stream()
                .filter(s -> "小李".equals(s.getDisplayName())).findFirst().orElseThrow();
        assertEquals(1, xiaoli2.getItems().size());
        assertEquals(1600L, xiaoli2.getSubtotalCents());
    }

    @Test
    void sandboxMenu_viaSandboxMcpClient_noToken() {
        // sandbox.enabled 默认 true → SandboxMcpClient 顶替，返回 A1 探针 fixture
        var menu = menuAdapter.listMeals("190001", "1", "1");
        assertFalse(menu.getMeals().isEmpty(), "沙盒菜单应有餐品");
        assertFalse(menu.getCategories().isEmpty(), "沙盒菜单应有分类");

        var coupons = couponAdapter.listStoreCoupons("190001", "1", "1");
        assertEquals(2, coupons.size(), "沙盒券应有 2 张");
        assertTrue(coupons.stream().anyMatch(c -> c.getTitle().contains("美式")));
    }

    private Long planParticipantId(Long planId, String displayName) {
        return service.getPlan(planId).getParticipants().stream()
                .filter(p -> displayName.equals(p.getDisplayName())).findFirst().orElseThrow().getId();
    }
}
