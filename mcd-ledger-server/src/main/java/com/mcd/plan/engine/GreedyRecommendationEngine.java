package com.mcd.plan.engine;

import com.mcd.plan.domain.PlanRecommendation;
import com.mcd.plan.engine.rule.ComboRule;
import com.mcd.plan.engine.rule.CouponRule;
import com.mcd.plan.engine.rule.RecommendRule;
import com.mcd.plan.engine.rule.ThresholdRule;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 贪心枚举推荐引擎（设计 §3.2）：R1 套餐组合 / R2 满减凑单 / R3 券匹配。
 *
 * <p>不依赖 Web 框架与 McpClient（输入是 {@link PlanContext}），可纯单测。
 * 输出按 netSavingCents 降序（NULL 排最后），≤0 的项打 notWorthIt 标记但<b>不剔除</b>（R6 反诱导）。
 */
@Component
public class GreedyRecommendationEngine implements RecommendationEngine {

    private final List<RecommendRule> rules;

    public GreedyRecommendationEngine(ComboRule comboRule, ThresholdRule thresholdRule,
                                      CouponRule couponRule) {
        this.rules = List.of(comboRule, thresholdRule, couponRule);
    }

    @Override
    public List<PlanRecommendation> recommend(PlanContext ctx) {
        List<PlanRecommendation> all = new ArrayList<>();
        for (RecommendRule rule : rules) {
            all.addAll(rule.apply(ctx));
        }
        for (PlanRecommendation rec : all) {
            // R6：券匹配（NULL）不算不划算，只是不可核算；有金额且 ≤0 才灰显
            rec.setNotWorthIt(rec.getNetSavingCents() != null && rec.getNetSavingCents() <= 0L);
            rec.validateR6();
        }
        // 有金额者按净节省降序；无金额（COUPON）恒定排最后，内部保持规则产出顺序
        all.sort(Comparator.comparing(PlanRecommendation::getNetSavingCents,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(r -> r.getType() == null ? "" : r.getType()));
        return all;
    }
}
