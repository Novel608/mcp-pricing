package com.mcd.plan.engine.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcd.ledger.common.Money;
import com.mcd.plan.domain.PlanRecommendation;
import com.mcd.plan.domain.enums.PriceSource;
import com.mcd.plan.domain.enums.RecommendType;
import com.mcd.plan.engine.PlanContext;
import com.mcd.plan.engine.PlanRuleProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * R2 满减凑单（设计 §3.2 / §3.3）。
 *
 * <p>addedCost = 门槛 − 当前小计（已达标为 0）；netSaving = 优惠 − addedCost。
 * 档位（门槛/优惠）来自 `PlanRuleProperties` 活动规则表；**当前小计由官方菜单参考价算出**，
 * 因此 priceSource = {@link PriceSource#MCP_REFERENCE}，文案固定「依据菜单参考价计算」。
 *
 * <p>负例（净亏）照常产出，由引擎打 notWorthIt 标记灰显，不隐藏、不剔除（R6）。
 */
@Component
public class ThresholdRule implements RecommendRule {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public RecommendType type() {
        return RecommendType.THRESHOLD;
    }

    @Override
    public List<PlanRecommendation> apply(PlanContext ctx) {
        List<PlanRecommendation> out = new ArrayList<>();
        if (ctx.thresholds() == null) {
            return out;
        }
        long current = ctx.currentSubtotalCents();
        for (PlanRuleProperties.ThresholdDefinition t : ctx.thresholds()) {
            long gap = Math.max(0L, t.getThresholdCents() - current);
            long discount = t.getDiscountCents();
            long net = discount - gap;

            PlanRecommendation rec = new PlanRecommendation();
            rec.setType(RecommendType.THRESHOLD.name());
            rec.setTitle("满减凑单：" + t.getTitle()
                    + (net > 0 ? " 净省 " + Money.formatYuan(net) : " 不划算"));
            rec.setDiscountCents(discount);
            rec.setAddedCostCents(gap);
            rec.setNetSavingCents(net);
            rec.setPriceSource(PriceSource.MCP_REFERENCE.name());

            ObjectNode n = mapper.createObjectNode();
            n.put("type", "THRESHOLD");
            n.put("thresholdCents", t.getThresholdCents());
            n.put("currentCents", current);
            n.put("gapCents", gap);
            n.put("discountCents", discount);
            n.put("addedCostCents", gap);
            n.put("text", text(t, current, gap, discount, net));
            rec.setReasonJson(n.toString());
            rec.validateR6();
            out.add(rec);
        }
        return out;
    }

    private static String text(PlanRuleProperties.ThresholdDefinition t, long current, long gap,
                               long discount, long net) {
        String rule = "「" + t.getTitle() + "」";
        if (gap == 0L) {
            return "当前小计 " + Money.formatYuan(current) + " 已达 " + rule
                    + "，预计省 " + Money.formatYuan(discount)
                    + "（依据菜单参考价计算；活动规则以门店公示为准）";
        }
        if (net > 0) {
            return "再加 " + Money.formatYuan(gap) + " 可享 " + rule
                    + "，净省 " + Money.formatYuan(net)
                    + "（依据菜单参考价计算；活动规则以门店公示为准）";
        }
        return "再加 " + Money.formatYuan(gap) + " 才够 " + rule
                + "，净亏 " + Money.formatYuan(-net) + "，不建议（依据菜单参考价计算）";
    }
}
