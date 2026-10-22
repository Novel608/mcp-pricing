package com.mcd.plan.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mcd.ledger.common.BizException;
import com.mcd.plan.domain.MealPlan;
import com.mcd.plan.domain.PlanRecommendation;
import com.mcd.plan.domain.enums.RecommendType;
import com.mcd.plan.dto.CouponVO;
import com.mcd.plan.dto.ItemAggregateVO;
import com.mcd.plan.engine.rule.ComboRule;
import com.mcd.plan.engine.rule.CouponRule;
import com.mcd.plan.engine.rule.ThresholdRule;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * R6 净节省计算单测（含负例）：引擎不依赖 Web 框架与 McpClient，入参是 PlanContext。
 *
 * <p>价格全部模拟「官方菜单参考价」：麦辣鸡腿汉堡 2250 / 薯条 1350 / 可乐 950 / 板烧鸡腿堡 2350，
 * 麦辣鸡腿汉堡三件套 3350（单套散点 4550 → 单套省 1200）。
 */
class GreedyRecommendationEngineTest {

    private static final Map<String, Long> PRICES = new HashMap<>();

    static {
        PRICES.put("1440", 2250L);
        PRICES.put("4810", 1350L);
        PRICES.put("9900008751", 950L);
        PRICES.put("1406", 2350L);
        PRICES.put("9900005456", 3350L); // 麦辣鸡腿汉堡三件套
        PRICES.put("9900005462", 3450L); // 板烧鸡腿堡三件套
    }

    private final GreedyRecommendationEngine engine =
            new GreedyRecommendationEngine(new ComboRule(), new ThresholdRule(), new CouponRule());

    @Test
    void combo_twoSets_discount2400_netSaving2400() {
        List<ItemAggregateVO> agg = Arrays.asList(agg("1440", 2), agg("4810", 2), agg("9900008751", 2));
        PlanContext ctx = ctx(agg, List.of(comboMcChicken()), List.of(), 9100L);

        List<PlanRecommendation> recs = engine.recommend(ctx);
        PlanRecommendation combo = first(recs, RecommendType.COMBO);
        assertEquals(2400L, combo.getDiscountCents());      // (4550-3350) × 2
        assertEquals(0L, combo.getAddedCostCents());
        assertEquals(2400L, combo.getNetSavingCents());
        assertFalse(combo.getNotWorthIt());
        assertEquals("MCP_REFERENCE", combo.getPriceSource());
        assertTrue(combo.getReasonJson().contains("麦辣鸡腿汉堡三件套"));
    }

    @Test
    void greedy_noDoubleCount_secondComboSkippedWhenStockConsumed() {
        // 薯条/可乐只有 2 份，已被「麦辣三件套 ×2」吃光 → 板烧三件套应无套数可组
        List<ItemAggregateVO> agg = Arrays.asList(
                agg("1440", 2), agg("4810", 2), agg("9900008751", 2), agg("1406", 1));
        PlanContext ctx = ctx(agg, Arrays.asList(comboMcChicken(), comboGrilled()), List.of(), 11450L);

        List<PlanRecommendation> recs = engine.recommend(ctx);
        assertEquals(1, recs.stream().filter(r -> RecommendType.COMBO.name().equals(r.getType())).count(),
                "同一份餐品不得被两条推荐重复计算");
    }

    @Test
    void threshold_positiveAndNegative_netSavingSigned() {
        PlanRuleProperties.ThresholdDefinition t1 = tier("满 100 减 15", 10000L, 1500L);
        PlanRuleProperties.ThresholdDefinition t2 = tier("满 200 减 30", 20000L, 3000L);
        PlanRuleProperties.ThresholdDefinition t3 = tier("满 500 减 50", 50000L, 5000L);
        PlanContext ctx = ctx(List.of(agg("1440", 1)), List.of(), Arrays.asList(t1, t2, t3), 2250L);

        List<PlanRecommendation> recs = engine.recommend(ctx);
        List<PlanRecommendation> th = recs.stream()
                .filter(r -> RecommendType.THRESHOLD.name().equals(r.getType())).toList();
        assertEquals(3, th.size());
        // 小计 2250：满100 缺口 7750 → 净亏 6250（灰显）
        assertEquals(-6250L, th.get(0).getNetSavingCents());
        assertTrue(th.get(0).getNotWorthIt());
        // 满200 缺口 17750 → 净亏 14750
        assertEquals(-14750L, th.get(1).getNetSavingCents());
        // 满500 缺口 47750 → 净亏 42750
        assertEquals(-42750L, th.get(2).getNetSavingCents());
    }

    @Test
    void threshold_reached_zeroGap_fullDiscountIsNetSaving() {
        PlanRuleProperties.ThresholdDefinition t = tier("满 100 减 15", 10000L, 1500L);
        PlanContext ctx = ctx(List.of(agg("1440", 5)), List.of(), List.of(t), 11250L);

        PlanRecommendation r = first(engine.recommend(ctx), RecommendType.THRESHOLD);
        assertEquals(0L, r.getAddedCostCents());
        assertEquals(1500L, r.getNetSavingCents());
        assertFalse(r.getNotWorthIt());
    }

    @Test
    void coupon_netSavingAlwaysNull_andNotWorthItFalse() {
        CouponVO c = new CouponVO();
        c.setTitle("新人专享-中杯美式7折");
        c.setCouponId("CPN_1");
        CouponVO.ProductVO p = new CouponVO.ProductVO();
        p.setProductCode("1440");
        p.setProductName("麦辣鸡腿汉堡");
        c.getProducts().add(p);

        PlanContext ctx = ctx(List.of(agg("1440", 1)), List.of(), List.of(), List.of(c), 2250L);
        PlanRecommendation r = first(engine.recommend(ctx), RecommendType.COUPON);
        assertNull(r.getNetSavingCents(), "R3 券匹配一律不给净节省金额");
        assertEquals(0L, r.getDiscountCents(), "官方未返回券面额 → 不臆造");
        assertFalse(r.getNotWorthIt());
        assertTrue(r.getReasonJson().contains("\"applicable\":true"));
        assertTrue(r.getTitle().contains("可用"));
    }

    @Test
    void r6_couponWithNetSaving_mustThrow() {
        PlanRecommendation r = new PlanRecommendation();
        r.setType(RecommendType.COUPON.name());
        r.setDiscountCents(1000L);
        r.setAddedCostCents(0L);
        r.setNetSavingCents(1000L);
        r.setPriceSource("MCP_REFERENCE");
        r.setReasonJson("{\"type\":\"COUPON\"}");
        assertThrows(BizException.class, r::validateR6);
    }

    @Test
    void r6_comboNetSavingMismatch_mustThrow() {
        PlanRecommendation r = new PlanRecommendation();
        r.setType(RecommendType.COMBO.name());
        r.setDiscountCents(2400L);
        r.setAddedCostCents(0L);
        r.setNetSavingCents(9999L);
        r.setPriceSource("MCP_REFERENCE");
        r.setReasonJson("{\"type\":\"COMBO\"}");
        assertThrows(BizException.class, r::validateR6);
    }

    @Test
    void ordering_netSavingDesc_couponLast() {
        CouponVO c = new CouponVO();
        c.setTitle("券");
        c.setCouponId("C");
        PlanRuleProperties.ThresholdDefinition t = tier("满 100 减 15", 10000L, 1500L);
        List<ItemAggregateVO> agg = Arrays.asList(agg("1440", 2), agg("4810", 2), agg("9900008751", 2));
        PlanContext ctx = ctx(agg, List.of(comboMcChicken()), List.of(t), List.of(c), 9100L);

        List<PlanRecommendation> recs = engine.recommend(ctx);
        // 满100：缺口 900 → 净省 600；套餐：2400 → 套餐在前
        assertEquals(RecommendType.COMBO.name(), recs.get(0).getType());
        assertEquals(RecommendType.THRESHOLD.name(), recs.get(1).getType());
        assertEquals(RecommendType.COUPON.name(), recs.get(recs.size() - 1).getType());
        assertNull(recs.get(recs.size() - 1).getNetSavingCents());
    }

    // ===== helpers =====

    private static ItemAggregateVO agg(String code, int qty) {
        ItemAggregateVO v = new ItemAggregateVO();
        v.setExternalItemId(code);
        v.setName(code);
        v.setTotalQuantity(qty);
        Long unit = PRICES.get(code);
        v.setSubtotalCents(unit == null ? 0L : unit * qty);
        return v;
    }

    private static PlanContext ctx(List<ItemAggregateVO> agg,
                                   List<PlanRuleProperties.ComboDefinition> combos,
                                   List<PlanRuleProperties.ThresholdDefinition> tiers,
                                   long subtotal) {
        return ctx(agg, combos, tiers, List.of(), subtotal);
    }

    private static PlanContext ctx(List<ItemAggregateVO> agg,
                                   List<PlanRuleProperties.ComboDefinition> combos,
                                   List<PlanRuleProperties.ThresholdDefinition> tiers,
                                   List<CouponVO> coupons, long subtotal) {
        MealPlan plan = new MealPlan();
        plan.setId(1L);
        plan.setStoreId("190001");
        return new PlanContext(plan, new ArrayList<>(agg), PRICES, coupons, combos, tiers, subtotal);
    }

    private static PlanRuleProperties.ComboDefinition comboMcChicken() {
        PlanRuleProperties.ComboDefinition c = new PlanRuleProperties.ComboDefinition();
        c.setComboItemId("9900005456");
        c.setName("麦辣鸡腿汉堡三件套");
        c.setCovers(Arrays.asList(cover("1440", 1), cover("4810", 1), cover("9900008751", 1)));
        return c;
    }

    private static PlanRuleProperties.ComboDefinition comboGrilled() {
        PlanRuleProperties.ComboDefinition c = new PlanRuleProperties.ComboDefinition();
        c.setComboItemId("9900005462");
        c.setName("板烧鸡腿堡三件套");
        c.setCovers(Arrays.asList(cover("1406", 1), cover("4810", 1), cover("9900008751", 1)));
        return c;
    }

    private static PlanRuleProperties.Cover cover(String itemId, int qty) {
        PlanRuleProperties.Cover c = new PlanRuleProperties.Cover();
        c.setItemId(itemId);
        c.setName(itemId);
        c.setQuantity(qty);
        return c;
    }

    private static PlanRuleProperties.ThresholdDefinition tier(String title, long threshold, long discount) {
        PlanRuleProperties.ThresholdDefinition t = new PlanRuleProperties.ThresholdDefinition();
        t.setTitle(title);
        t.setThresholdCents(threshold);
        t.setDiscountCents(discount);
        return t;
    }

    private static PlanRecommendation first(List<PlanRecommendation> recs, RecommendType type) {
        return recs.stream().filter(r -> type.name().equals(r.getType())).findFirst().orElseThrow();
    }
}
