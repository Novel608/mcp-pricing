package com.mcd.plan.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mcd.plan.domain.PlanRecommendation;
import com.mcd.plan.dto.RecommendationVO;
import com.mcd.plan.mapper.PlanRecommendationMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 推荐端到端（设计 §3.4 #7/#8）：沙盒方案 id=1 → R1 套餐 / R2 满减 / R3 券。
 * 断言的金额全部来自官方 query-meals 抓取值：散点三件套 4550 vs 套餐 3350 → 单套省 1200 × 2 套 = 2400。
 */
@SpringBootTest(classes = com.mcd.ledger.LedgerApplication.class,
        properties = "spring.datasource.url=jdbc:h2:mem:rectest;MODE=MySQL;DB_CLOSE_ON_EXIT=FALSE")
class PlanRecommendationIntegrationTest {

    @Autowired
    private RecommendationService recService;
    @Autowired
    private PlanRecommendationMapper recMapper;

    @Test
    void recommend_sandboxPlan_threeRuleTypesInOrder() {
        List<RecommendationVO> recs = recService.recommend(1L);
        // R2 满200减30（缺口 350 → 净省 2650）> R1 套餐（2400）> R2 满100减15（1500）> R3 券（NULL）
        assertTrue(recs.size() >= 5, "应产出 1 条套餐 + 2 条满减 + 2 条券，实际 " + recs.size());
        assertEquals(2650L, recs.get(0).getNetSavingCents());
        assertTrue(recs.get(0).getTitle().contains("满 200 减 30"));

        RecommendationVO combo = recs.stream().filter(r -> "COMBO".equals(r.getType())).findFirst().orElseThrow();
        assertEquals(2400L, combo.getDiscountCents());
        assertEquals(0L, combo.getAddedCostCents());
        assertEquals(2400L, combo.getNetSavingCents());
        assertFalse(combo.getNotWorthIt());
        assertEquals("MCP_REFERENCE", combo.getPriceSource());
        assertNotNull(combo.getId(), "推荐必须落库（plan_recommendation）");

        long coupons = recs.stream().filter(r -> "COUPON".equals(r.getType())).count();
        assertEquals(2, coupons);
        assertTrue(recs.stream().filter(r -> "COUPON".equals(r.getType()))
                .allMatch(r -> r.getNetSavingCents() == null), "R3 净节省一律 NULL");
        // NULL 排最后
        assertNull(recs.get(recs.size() - 1).getNetSavingCents());
    }

    @Test
    void accept_keepsRecord_acrossRecompute() {
        List<RecommendationVO> first = recService.recommend(1L);
        RecommendationVO target = first.get(0);
        recService.accept(1L, target.getId(), true);

        List<RecommendationVO> second = recService.recommend(1L);
        RecommendationVO kept = second.stream()
                .filter(r -> target.getId().equals(r.getId())).findFirst().orElseThrow();
        assertTrue(kept.getAccepted());
        long dup = second.stream().filter(r -> target.getTitle().equals(r.getTitle())).count();
        assertEquals(1, dup, "已采纳的同款推荐不得重复插入");
    }

    @Test
    void preview_doesNotPersist() {
        long before = count(1L);
        List<RecommendationVO> pv = recService.preview(1L);
        assertFalse(pv.isEmpty());
        assertEquals(before, count(1L), "预演为只读，不得落库");
    }

    private long count(Long planId) {
        Long n = recMapper.selectCount(new LambdaQueryWrapper<PlanRecommendation>()
                .eq(PlanRecommendation::getPlanId, planId));
        return n == null ? 0L : n;
    }
}
