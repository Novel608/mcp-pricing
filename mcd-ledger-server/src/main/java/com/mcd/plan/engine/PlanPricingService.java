package com.mcd.plan.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcd.ledger.common.BizException;
import com.mcd.ledger.common.ErrorCode;
import com.mcd.ledger.common.HashUtil;
import com.mcd.ledger.engine.PennyAllocator;
import com.mcd.plan.domain.MealPlan;
import com.mcd.plan.domain.PlanSnapshot;
import com.mcd.plan.domain.enums.PlanConfidence;
import com.mcd.plan.dto.ItemAggregateVO;
import com.mcd.plan.dto.ParticipantSelectionVO;
import com.mcd.plan.dto.PlanDetailVO;
import com.mcd.plan.dto.PricingResult;
import com.mcd.plan.dto.SnapshotVO;
import com.mcd.plan.mapper.MealPlanMapper;
import com.mcd.plan.mapper.PlanSnapshotMapper;
import com.mcd.plan.mcp.PriceAdapter;
import com.mcd.plan.service.MealPlanService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 官方核价 → 预期基线（设计 §3.2 / §3.4 #9/#10，R5）。
 *
 * <p>流程：官方 `calculate-price` 试算 → 按「各人餐品小计」权重用 {@link PennyAllocator}
 * 摊到人 → <b>R5 预期恒等断言</b>（Σ 每人 == 官方总额，尾差必须为 0）→ 落 `plan_snapshot`。
 * 权重口径与事后对账完全一致，保证「预期 vs 实际」讲的是同一件事。
 */
@Service
public class PlanPricingService {

    private final MealPlanService planService;
    private final MealPlanMapper planMapper;
    private final PlanSnapshotMapper snapshotMapper;
    private final PriceAdapter priceAdapter;
    private final long staleThresholdMinutes;
    private final ObjectMapper mapper = new ObjectMapper();

    public PlanPricingService(MealPlanService planService, MealPlanMapper planMapper,
                              PlanSnapshotMapper snapshotMapper, PriceAdapter priceAdapter,
                              @Value("${mcd.plan.stale-threshold-minutes:30}") long staleThresholdMinutes) {
        this.planService = planService;
        this.planMapper = planMapper;
        this.snapshotMapper = snapshotMapper;
        this.priceAdapter = priceAdapter;
        this.staleThresholdMinutes = staleThresholdMinutes;
    }

    /** 触发官方核价并落基线（会刷新为该方案的最新基线）。 */
    public SnapshotVO price(Long planId, String orderType, String beType) {
        PlanDetailVO vo = planService.getPlan(planId);
        if (vo.getAggregates() == null || vo.getAggregates().isEmpty()) {
            throw BizException.of(ErrorCode.PARAM_ERROR, "选餐为空，无法核价");
        }
        if (vo.getSummary() == null || vo.getSummary().getPerPerson().isEmpty()) {
            throw BizException.of(ErrorCode.PARAM_ERROR, "方案无成员，无法核价");
        }

        List<String[]> items = new ArrayList<>();
        for (ItemAggregateVO a : vo.getAggregates()) {
            items.add(new String[]{a.getExternalItemId(), String.valueOf(a.getTotalQuantity())});
        }
        PricingResult r = priceAdapter.calculatePrice(vo.getStoreId(), orderType, beType, items);

        long subtotal = r.getProductPriceCents() != 0L ? r.getProductPriceCents()
                : r.getProductOriginalPriceCents();
        long delivery = r.getDeliveryPriceCents();
        long total = r.getTotalCents() != 0L ? r.getTotalCents() : subtotal + delivery;

        List<ParticipantSelectionVO> persons = vo.getSelections();
        List<Integer> weights = new ArrayList<>();
        long weightSum = 0L;
        for (ParticipantSelectionVO p : persons) {
            long w = p.getSubtotalCents() == null ? 0L : p.getSubtotalCents();
            weightSum += w;
            weights.add(w > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) w);
        }
        if (weightSum == 0L) {
            // 全员小计为 0（未记录单价）→ 退化为人数均摊，权重全 1
            weights = new ArrayList<>(Collections.nCopies(persons.size(), 1));
        }
        List<Long> alloc = PennyAllocator.allocate(total, weights);
        assertExpectedIdentity(alloc, total);

        List<SnapshotVO.PerPersonExpected> per = new ArrayList<>();
        for (int i = 0; i < persons.size(); i++) {
            SnapshotVO.PerPersonExpected e = new SnapshotVO.PerPersonExpected();
            e.setParticipantId(persons.get(i).getParticipantId());
            e.setDisplayName(persons.get(i).getDisplayName());
            e.setExpectedCents(alloc.get(i));
            per.add(e);
        }

        PlanSnapshot snap = new PlanSnapshot();
        snap.setPlanId(planId);
        snap.setExpectedSubtotalCents(subtotal);
        snap.setExpectedDiscountCents(r.getDiscountCents());
        snap.setExpectedDeliveryFeeCents(delivery);
        snap.setExpectedTotalCents(total);
        snap.setPerPersonExpectedJson(toJson(per));
        snap.setPricedAt(LocalDateTime.now());
        snap.setMenuCacheVersion("query-meals:" + vo.getStoreId());
        snap.setItemsHash(itemsHash(vo.getAggregates()));
        snap.setConfidence(PlanConfidence.ESTIMATED_CURRENT.name());
        snap.setPartial(r.isPartial());
        snap.setCreatedAt(LocalDateTime.now());
        snapshotMapper.insert(snap);

        MealPlan plan = planMapper.selectById(planId);
        if (plan != null && !"CONVERTED".equals(plan.getStatus())) {
            plan.setStatus("PRICED");
            planMapper.updateById(plan);
        }
        return toVO(snap);
    }

    /** 读取最新基线；无基线返回 null（供主读接口组装，不抛错）。 */
    public SnapshotVO latestOrNull(Long planId) {
        PlanSnapshot snap = latestRow(planId);
        if (snap == null) {
            return null;
        }
        SnapshotVO vo = toVO(snap);
        PlanDetailVO cur = planService.getPlan(planId);
        boolean staleItems = !snap.getItemsHash().equals(itemsHash(cur.getAggregates()));
        boolean staleTime = snap.getPricedAt() != null
                && snap.getPricedAt().plusMinutes(staleThresholdMinutes).isBefore(LocalDateTime.now());
        if (staleItems) {
            vo.setStale(true);
            vo.setStaleReason("核价后选餐已变更，基线不再匹配当前选餐");
        } else if (staleTime) {
            vo.setStale(true);
            vo.setStaleReason("报价已超过 " + staleThresholdMinutes + " 分钟，建议重新核价");
        } else {
            vo.setStale(false);
        }
        return vo;
    }

    /** 读取最新基线；无基线抛 PLAN_BASELINE_UNAVAILABLE（404）。stale 为软提示，不阻断。 */
    public SnapshotVO latest(Long planId) {
        PlanSnapshot snap = latestRow(planId);
        if (snap == null) {
            throw BizException.of(ErrorCode.PLAN_BASELINE_UNAVAILABLE,
                    "该方案尚无官方核价基线，请先调用 /plans/{id}/pricing");
        }
        SnapshotVO vo = toVO(snap);
        PlanDetailVO cur = planService.getPlan(planId);
        if (!snap.getItemsHash().equals(itemsHash(cur.getAggregates()))) {
            vo.setStale(true);
            vo.setStaleReason("核价后选餐已变更，基线不再匹配当前选餐");
        } else if (snap.getPricedAt() != null
                && snap.getPricedAt().plusMinutes(staleThresholdMinutes).isBefore(LocalDateTime.now())) {
            vo.setStale(true);
            vo.setStaleReason("报价已超过 " + staleThresholdMinutes + " 分钟，建议重新核价");
        } else {
            vo.setStale(false);
        }
        return vo;
    }

    /** 供 T05 转出复用：取最新基线行（可为 null）。 */
    public PlanSnapshot latestRow(Long planId) {
        List<PlanSnapshot> rows = snapshotMapper.selectList(new LambdaQueryWrapper<PlanSnapshot>()
                .eq(PlanSnapshot::getPlanId, planId)
                .orderByDesc(PlanSnapshot::getId));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 选餐指纹：餐品 code × 数量，用于 stale 判定（确定性排序 → 幂等）。 */
    public static String itemsHash(List<ItemAggregateVO> aggregates) {
        List<String> parts = new ArrayList<>();
        for (ItemAggregateVO a : aggregates) {
            parts.add(a.getExternalItemId() + "x" + a.getTotalQuantity());
        }
        Collections.sort(parts);
        return HashUtil.sha256Hex(String.join("|", parts)).substring(0, 32);
    }

    /** R5 预期恒等：Σ 每人预期 == 官方总额，否则阻断（理论上不该发生）。 */
    private void assertExpectedIdentity(List<Long> alloc, long total) {
        long sum = 0L;
        for (Long v : alloc) {
            sum += v;
        }
        if (sum != total) {
            throw BizException.of(ErrorCode.ENGINE_IDENTITY_BROKEN,
                    "R5 预期恒等被破坏：Σ每人 " + sum + " ≠ 官方总额 " + total);
        }
    }

    private String toJson(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw BizException.of(ErrorCode.INTERNAL_ERROR, "基线 JSON 序列化失败");
        }
    }

    private SnapshotVO toVO(PlanSnapshot s) {
        SnapshotVO vo = new SnapshotVO();
        vo.setId(s.getId());
        vo.setPlanId(s.getPlanId());
        vo.setExpectedSubtotalCents(s.getExpectedSubtotalCents());
        vo.setExpectedDiscountCents(s.getExpectedDiscountCents());
        vo.setExpectedDeliveryFeeCents(s.getExpectedDeliveryFeeCents());
        vo.setExpectedTotalCents(s.getExpectedTotalCents());
        vo.setPricedAt(s.getPricedAt());
        vo.setConfidence(s.getConfidence());
        vo.setPartial(s.getPartial());
        vo.setItemsHash(s.getItemsHash());
        vo.setMenuCacheVersion(s.getMenuCacheVersion());
        try {
            SnapshotVO.PerPersonExpected[] arr = mapper.readValue(s.getPerPersonExpectedJson(),
                    SnapshotVO.PerPersonExpected[].class);
            for (SnapshotVO.PerPersonExpected e : arr) {
                vo.getPerPerson().add(e);
            }
        } catch (Exception ignore) {
            // 基线 JSON 异常不阻断读接口
        }
        return vo;
    }
}
