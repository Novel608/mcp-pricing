package com.mcd.plan.engine.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcd.ledger.common.Money;
import com.mcd.plan.domain.PlanRecommendation;
import com.mcd.plan.domain.enums.PriceSource;
import com.mcd.plan.domain.enums.RecommendType;
import com.mcd.plan.dto.ItemAggregateVO;
import com.mcd.plan.engine.PlanContext;
import com.mcd.plan.engine.PlanRuleProperties;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * R1 套餐组合（设计 §3.2 / §3.3）：散点单品 → 官方套餐。
 *
 * <p>净节省 = Σ(单品官方单价 × 一套内份数) − 套餐官方价，再 × 可合成的套数。
 * 组合构成来自 `PlanRuleProperties`（活动信息），<b>两个价格都取自官方菜单参考价</b>，
 * 因此 priceSource = {@link PriceSource#MCP_REFERENCE}。
 *
 * <p>贪心：单份净节省高者优先，合成后消耗库存再枚举下一候选，
 * 保证同一份餐品不会被两条推荐重复计算（凑单诱导风险，设计 T-3）。
 */
@Component
public class ComboRule implements RecommendRule {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public RecommendType type() {
        return RecommendType.COMBO;
    }

    @Override
    public List<PlanRecommendation> apply(PlanContext ctx) {
        List<PlanRecommendation> out = new ArrayList<>();
        if (ctx.candidateCombos() == null || ctx.aggregates() == null) {
            return out;
        }
        // 库存：餐品 code → 数量（合成即扣减）
        Map<String, Integer> stock = new LinkedHashMap<>();
        for (ItemAggregateVO a : ctx.aggregates()) {
            stock.merge(a.getExternalItemId(), a.getTotalQuantity(), Integer::sum);
        }

        List<Candidate> candidates = new ArrayList<>();
        for (PlanRuleProperties.ComboDefinition combo : ctx.candidateCombos()) {
            Long comboPrice = ctx.priceOf(combo.getComboItemId());
            if (comboPrice == null) {
                continue; // 官方菜单查无此套餐 → 无官方价格依据，不出推荐（L3：不臆造价格）
            }
            long perSetStandalone = 0L;
            boolean priced = true;
            for (PlanRuleProperties.Cover c : combo.getCovers()) {
                Long unit = ctx.priceOf(c.getItemId());
                if (unit == null) {
                    priced = false;
                    break;
                }
                perSetStandalone += unit * Math.max(1, c.getQuantity());
            }
            if (!priced) {
                continue;
            }
            candidates.add(new Candidate(combo, comboPrice, perSetStandalone));
        }
        // 单套净节省高者优先
        candidates.sort(Comparator.comparingLong(Candidate::discountPerSet).reversed());

        for (Candidate cand : candidates) {
            PlanRuleProperties.ComboDefinition combo = cand.combo();
            int sets = Integer.MAX_VALUE;
            for (PlanRuleProperties.Cover c : combo.getCovers()) {
                int need = Math.max(1, c.getQuantity());
                int have = stock.getOrDefault(c.getItemId(), 0);
                sets = Math.min(sets, have / need);
            }
            if (sets <= 0) {
                continue;
            }
            for (PlanRuleProperties.Cover c : combo.getCovers()) {
                int need = Math.max(1, c.getQuantity());
                stock.put(c.getItemId(), stock.getOrDefault(c.getItemId(), 0) - need * sets);
            }

            long discount = cand.discountPerSet() * sets;
            long addedCost = 0L;
            PlanRecommendation rec = new PlanRecommendation();
            rec.setType(RecommendType.COMBO.name());
            rec.setTitle("套餐组合：" + comboName(combo) + " ×" + sets
                    + (discount > 0 ? " 省 " + Money.formatYuan(discount) : " 不划算"));
            rec.setDiscountCents(discount);
            rec.setAddedCostCents(addedCost);
            rec.setNetSavingCents(discount - addedCost);
            rec.setPriceSource(PriceSource.MCP_REFERENCE.name());
            rec.setReasonJson(reason(ctx, combo, sets, cand, discount, addedCost));
            rec.validateR6();
            out.add(rec);
        }
        return out;
    }

    private String reason(PlanContext ctx, PlanRuleProperties.ComboDefinition combo, int sets,
                          Candidate cand, long discount, long addedCost) {
        ObjectNode n = mapper.createObjectNode();
        n.put("type", "COMBO");
        n.put("comboItemId", combo.getComboItemId());
        n.put("comboName", comboName(combo));
        n.put("comboPriceCents", cand.comboPrice());
        n.put("sets", sets);
        ArrayNode covered = n.putArray("coveredItems");
        StringBuilder parts = new StringBuilder();
        for (PlanRuleProperties.Cover c : combo.getCovers()) {
            int need = Math.max(1, c.getQuantity());
            ObjectNode ci = covered.addObject();
            ci.put("itemId", c.getItemId());
            ci.put("name", coverName(ctx, c));
            ci.put("qty", need * sets);
            ci.put("unitCents", ctx.priceOf(c.getItemId()));
            if (parts.length() > 0) {
                parts.append(" + ");
            }
            parts.append(need * sets).append(" 份").append(coverName(ctx, c));
        }
        n.put("standaloneTotalCents", cand.perSetStandalone() * sets);
        n.put("discountCents", discount);
        n.put("addedCostCents", addedCost);
        n.put("text", parts + " 可合成 " + sets + " 个「" + comboName(combo) + "」，"
                + (discount > 0 ? "立省 " + Money.formatYuan(discount)
                                : "反而多花 " + Money.formatYuan(-discount) + "，不建议")
                + "（依据菜单参考价计算）");
        return n.toString();
    }

    private static String comboName(PlanRuleProperties.ComboDefinition combo) {
        return (combo.getName() == null || combo.getName().isBlank())
                ? combo.getComboItemId() : combo.getName();
    }

    private static String coverName(PlanContext ctx, PlanRuleProperties.Cover c) {
        if (c.getName() != null && !c.getName().isBlank()) {
            return c.getName();
        }
        return ctx.aggregates().stream()
                .filter(a -> c.getItemId().equals(a.getExternalItemId()))
                .map(ItemAggregateVO::getName)
                .findFirst().orElse(c.getItemId());
    }

    /** 候选套餐及其官方价格算术结果。 */
    private record Candidate(PlanRuleProperties.ComboDefinition combo, long comboPrice,
                             long perSetStandalone) {

        long discountPerSet() {
            return perSetStandalone - comboPrice;
        }
    }
}
