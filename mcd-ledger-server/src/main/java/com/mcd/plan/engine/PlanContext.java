package com.mcd.plan.engine;

import com.mcd.plan.domain.MealPlan;
import com.mcd.plan.dto.CouponVO;
import com.mcd.plan.dto.ItemAggregateVO;
import java.util.List;
import java.util.Map;

/**
 * 推荐引擎入参（不可变，设计 §3.2）。
 *
 * <p>引擎<b>不依赖 Web 框架与 McpClient</b>：价格/券/聚合全部由上层装配后注入，
 * 因此 R1/R2/R3 可纯单测（设计 附录：可测性）。
 *
 * @param itemUnitPriceCents 官方菜单参考价（分）：优先 query-meals 现价，缺失回落选餐时记录的官方单价
 * @param currentSubtotalCents 当前选餐小计（分），来自各成员选餐 × 官方单价
 */
public record PlanContext(
        MealPlan plan,
        List<ItemAggregateVO> aggregates,
        Map<String, Long> itemUnitPriceCents,
        List<CouponVO> storeCoupons,
        List<PlanRuleProperties.ComboDefinition> candidateCombos,
        List<PlanRuleProperties.ThresholdDefinition> thresholds,
        long currentSubtotalCents) {

    /** 取官方单价；无官方依据返回 null（调用方必须跳过该餐品，严禁本地估算）。 */
    public Long priceOf(String itemId) {
        return itemUnitPriceCents.get(itemId);
    }
}
