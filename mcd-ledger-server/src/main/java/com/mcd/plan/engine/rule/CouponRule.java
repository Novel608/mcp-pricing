package com.mcd.plan.engine.rule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mcd.plan.domain.PlanRecommendation;
import com.mcd.plan.domain.enums.PriceSource;
import com.mcd.plan.domain.enums.RecommendType;
import com.mcd.plan.dto.CouponVO;
import com.mcd.plan.dto.ItemAggregateVO;
import com.mcd.plan.engine.PlanContext;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * R3 券匹配（设计 §3.2 / §3.3）：只展示、不领券、不给金额。
 *
 * <p>券面额虽是官方的，但「能否叠加 / 是否满足门槛」无从验证（设计 附录 A4），
 * 且官方 `query-store-coupons` 未返回券面额字段——<b>故 discountCents=0、netSavingCents 一律 NULL</b>，
 * 绝不臆造优惠金额。适用性仅做「券适用餐品 ∩ 已选餐品」的定性匹配。
 */
@Component
public class CouponRule implements RecommendRule {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public RecommendType type() {
        return RecommendType.COUPON;
    }

    @Override
    public List<PlanRecommendation> apply(PlanContext ctx) {
        List<PlanRecommendation> out = new ArrayList<>();
        if (ctx.storeCoupons() == null || ctx.aggregates() == null) {
            return out;
        }
        Set<String> selected = new LinkedHashSet<>();
        for (ItemAggregateVO a : ctx.aggregates()) {
            selected.add(a.getExternalItemId());
        }

        for (CouponVO c : ctx.storeCoupons()) {
            List<String> matched = new ArrayList<>();
            if (c.getProducts() != null) {
                for (CouponVO.ProductVO p : c.getProducts()) {
                    if (p.getProductCode() != null && selected.contains(p.getProductCode())) {
                        matched.add(p.getProductName() == null ? p.getProductCode() : p.getProductName());
                    }
                }
            }
            boolean applicable = !matched.isEmpty();
            Long netSaving = null; // R3 契约：净节省一律 NULL
            long discount = 0L;    // 官方未返回券面额 → 不臆造
            long addedCost = 0L;

            PlanRecommendation rec = new PlanRecommendation();
            rec.setType(RecommendType.COUPON.name());
            rec.setTitle("券匹配：" + (c.getTitle() == null ? c.getCouponId() : c.getTitle())
                    + (applicable ? "（可用）" : "（暂不适用）"));
            rec.setDiscountCents(discount);
            rec.setAddedCostCents(addedCost);
            rec.setNetSavingCents(netSaving);
            rec.setPriceSource(PriceSource.MCP_REFERENCE.name());

            ObjectNode n = mapper.createObjectNode();
            n.put("type", "COUPON");
            n.put("couponId", c.getCouponId());
            n.put("couponName", c.getTitle());
            n.put("applicable", applicable);
            if (applicable) {
                n.putNull("unmetReason");
            } else {
                n.put("unmetReason", "当前选餐未包含该券适用餐品");
            }
            ArrayNode arr = n.putArray("matchedItems");
            matched.forEach(arr::add);
            n.put("discountCents", discount);
            n.put("addedCostCents", addedCost);
            n.put("text", applicable
                    ? "当前选餐含适用餐品（" + String.join("、", matched) + "），该券可能可用，"
                            + "实际优惠以官方结算为准"
                    : "当前选餐未包含该券适用餐品，券面额与适用性以官方实际结算为准");
            rec.setReasonJson(n.toString());
            rec.validateR6();
            out.add(rec);
        }
        return out;
    }
}
