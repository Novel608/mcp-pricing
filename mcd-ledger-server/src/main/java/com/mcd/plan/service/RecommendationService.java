package com.mcd.plan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mcd.ledger.common.BizException;
import com.mcd.ledger.common.ErrorCode;
import com.mcd.plan.domain.MealPlan;
import com.mcd.plan.domain.PlanRecommendation;
import com.mcd.plan.dto.CouponVO;
import com.mcd.plan.dto.MealMenuVO;
import com.mcd.plan.dto.MealVO;
import com.mcd.plan.dto.ParticipantSelectionVO;
import com.mcd.plan.dto.PlanDetailVO;
import com.mcd.plan.dto.RecommendationVO;
import com.mcd.plan.dto.SelectionItemVO;
import com.mcd.plan.engine.GreedyRecommendationEngine;
import com.mcd.plan.engine.PlanContext;
import com.mcd.plan.engine.PlanRuleProperties;
import com.mcd.plan.engine.RecommendationEngine;
import com.mcd.plan.mapper.MealPlanMapper;
import com.mcd.plan.mapper.PlanRecommendationMapper;
import com.mcd.plan.mcp.CouponAdapter;
import com.mcd.plan.mcp.MenuAdapter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 推荐应用服务（设计 §3.4 #7/#8）：装配 {@link PlanContext} → 调引擎 → 落 `plan_recommendation`。
 *
 * <p>价格来源纪律：菜单现价取自官方 `query-meals`，缺失时回落「选餐时记录的官方单价」；
 * 两者皆无的餐品直接跳过（宁可少推荐，也不本地估算——L3）。
 * 菜单/券拉取失败只降级不中断（R1/R3 可能为空，R2 仍可用）。
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private final MealPlanService planService;
    private final MealPlanMapper planMapper;
    private final MenuAdapter menuAdapter;
    private final CouponAdapter couponAdapter;
    private final PlanRuleProperties ruleProps;
    private final RecommendationEngine engine;
    private final PlanRecommendationMapper recMapper;

    public RecommendationService(MealPlanService planService, MealPlanMapper planMapper,
                                 MenuAdapter menuAdapter, CouponAdapter couponAdapter,
                                 PlanRuleProperties ruleProps, GreedyRecommendationEngine engine,
                                 PlanRecommendationMapper recMapper) {
        this.planService = planService;
        this.planMapper = planMapper;
        this.menuAdapter = menuAdapter;
        this.couponAdapter = couponAdapter;
        this.ruleProps = ruleProps;
        this.engine = engine;
        this.recMapper = recMapper;
    }

    /** 触发 R1/R2/R3 计算并落库；重复调用为「重算」：未采纳的条目被替换，已采纳的保留。 */
    public List<RecommendationVO> recommend(Long planId) {
        PlanDetailVO vo = planService.getPlan(planId);
        MealPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw BizException.of(ErrorCode.PLAN_NOT_FOUND, "选餐方案不存在: " + planId);
        }

        List<PlanRecommendation> generated = compute(plan, vo);

        // 重算：清掉未采纳的旧条目（已采纳的留痕不动）
        recMapper.delete(new LambdaQueryWrapper<PlanRecommendation>()
                .eq(PlanRecommendation::getPlanId, planId)
                .and(w -> w.isNull(PlanRecommendation::getAccepted)
                        .or().eq(PlanRecommendation::getAccepted, false)));

        Set<String> keep = new HashSet<>();
        List<PlanRecommendation> acceptedOld = recMapper.selectList(
                new LambdaQueryWrapper<PlanRecommendation>()
                        .eq(PlanRecommendation::getPlanId, planId)
                        .eq(PlanRecommendation::getAccepted, true));
        for (PlanRecommendation r : acceptedOld) {
            keep.add(r.getType() + "|" + r.getTitle());
        }

        LocalDateTime now = LocalDateTime.now();
        for (PlanRecommendation r : generated) {
            if (keep.contains(r.getType() + "|" + r.getTitle())) {
                continue; // 已采纳的同款推荐不重复插入
            }
            r.setId(null);
            r.setPlanId(planId);
            r.setAccepted(false);
            r.setCreatedAt(now);
            r.validateR6();
            recMapper.insert(r);
        }
        return list(planId);
    }

    /** 只读预演：算出推荐但**不落库**（GET /demo/plan 用，避免读接口产生写副作用）。 */
    public List<RecommendationVO> preview(Long planId) {
        PlanDetailVO vo = planService.getPlan(planId);
        MealPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw BizException.of(ErrorCode.PLAN_NOT_FOUND, "选餐方案不存在: " + planId);
        }
        List<RecommendationVO> out = new ArrayList<>();
        for (PlanRecommendation r : compute(plan, vo)) {
            out.add(RecommendationVO.from(r));
        }
        out.sort(java.util.Comparator
                .comparing(RecommendationVO::getNetSavingCents,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())));
        return out;
    }

    /** 装配上下文 → 引擎计算（落库与预演共用）。 */
    private List<PlanRecommendation> compute(MealPlan plan, PlanDetailVO vo) {
        Map<String, Long> prices = officialUnitPrices(vo);
        List<CouponVO> coupons = safeCoupons(vo.getStoreId());
        PlanContext ctx = new PlanContext(plan, vo.getAggregates(), prices, coupons,
                ruleProps.getCombos(), ruleProps.getThresholds(), vo.getSummary().getTotalCents());
        return engine.recommend(ctx);
    }

    /** 读取方案推荐（引擎排序：净节省降序，券匹配 NULL 排最后）。 */
    public List<RecommendationVO> list(Long planId) {
        List<PlanRecommendation> rows = recMapper.selectList(
                new LambdaQueryWrapper<PlanRecommendation>()
                        .eq(PlanRecommendation::getPlanId, planId)
                        .orderByAsc(PlanRecommendation::getId));
        List<RecommendationVO> out = new ArrayList<>();
        for (PlanRecommendation r : rows) {
            RecommendationVO v = RecommendationVO.from(r);
            if (v.getNotWorthIt() == null) {
                v.setNotWorthIt(v.getNetSavingCents() != null && v.getNetSavingCents() <= 0L);
            }
            out.add(v);
        }
        // 与引擎同序：有金额者按净节省降序、NULL（券匹配）排最后；同额按 id 稳定
        out.sort(java.util.Comparator
                .comparing(RecommendationVO::getNetSavingCents,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder()))
                .thenComparing(RecommendationVO::getId));
        return out;
    }

    /** 采纳 / 取消采纳。 */
    public void accept(Long planId, Long recId, boolean accepted) {
        PlanRecommendation r = recMapper.selectById(recId);
        if (r == null || !planId.equals(r.getPlanId())) {
            throw BizException.of(ErrorCode.PLAN_NOT_FOUND, "推荐不存在: " + recId);
        }
        r.setAccepted(accepted);
        recMapper.updateById(r);
    }

    /** 官方单价表：菜单现价优先，回落选餐时记录的官方单价。 */
    private Map<String, Long> officialUnitPrices(PlanDetailVO vo) {
        Map<String, Long> prices = new HashMap<>();
        for (ParticipantSelectionVO s : vo.getSelections()) {
            for (SelectionItemVO it : s.getItems()) {
                if (it.getExternalItemId() != null && it.getUnitPriceCents() != null) {
                    prices.putIfAbsent(it.getExternalItemId(), it.getUnitPriceCents());
                }
            }
        }
        try {
            MealMenuVO menu = menuAdapter.listMeals(vo.getStoreId(), "1", "1");
            for (MealVO m : menu.getMeals()) {
                if (m.getCode() != null && m.getCurrentPriceCents() != null) {
                    prices.put(m.getCode(), m.getCurrentPriceCents());
                }
            }
        } catch (Exception e) {
            // 菜单不可达 → 保留选餐时单价继续，R1 可能为空（降级不中断）
            log.warn("菜单拉取失败，R1 推荐降级为空: storeId={}, msg={}", vo.getStoreId(), e.getMessage());
        }
        return prices;
    }

    private List<CouponVO> safeCoupons(String storeId) {
        try {
            List<CouponVO> c = couponAdapter.listStoreCoupons(storeId, "1", "1");
            return c == null ? new ArrayList<>() : c;
        } catch (Exception e) {
            log.warn("门店券拉取失败，R3 推荐降级为空: storeId={}, msg={}", storeId, e.getMessage());
            return new ArrayList<>();
        }
    }
}
