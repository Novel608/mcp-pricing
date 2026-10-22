package com.mcd.plan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mcd.ledger.common.BizException;
import com.mcd.ledger.common.ErrorCode;
import com.mcd.ledger.domain.MealLedger;
import com.mcd.ledger.dto.LedgerDetailVO;
import com.mcd.ledger.dto.ParticipantVO;
import com.mcd.ledger.dto.PersonRecon;
import com.mcd.ledger.dto.ReconciliationReport;
import com.mcd.ledger.mapper.MealLedgerMapper;
import com.mcd.ledger.service.LedgerService;
import com.mcd.plan.domain.PlanSnapshot;
import com.mcd.plan.dto.PlanVsActualVO;
import com.mcd.plan.dto.SnapshotVO;
import com.mcd.plan.engine.PlanPricingService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 预算 vs 实际差异（设计 §3.4 #12，DR-3）：**只读派生**，不落库、不进 ReconciliationEngine、不回写快照。
 *
 * <p>两侧口径：预期来自官方 `calculate-price` 基线，实际来自官方 `query-order` 订单，
 * 双官方口径成立才有意义（这也是 L3 禁止本地估算的根本理由）。
 * 未关联方案 → PLAN_NOT_LINKED；无基线 → PLAN_BASELINE_UNAVAILABLE（L2+ 下即恒定路径，C3）。
 */
@Service
public class PlanVsActualService {

    private final MealLedgerMapper ledgerMapper;
    private final LedgerService ledgerService;
    private final PlanPricingService pricingService;
    private final ObjectMapper mapper = new ObjectMapper();

    public PlanVsActualService(MealLedgerMapper ledgerMapper, LedgerService ledgerService,
                               PlanPricingService pricingService) {
        this.ledgerMapper = ledgerMapper;
        this.ledgerService = ledgerService;
        this.pricingService = pricingService;
    }

    public PlanVsActualVO vsPlan(Long ledgerId) {
        MealLedger ledger = ledgerMapper.selectById(ledgerId);
        if (ledger == null) {
            throw BizException.of(ErrorCode.LEDGER_NOT_FOUND, "餐账不存在: " + ledgerId);
        }
        if (ledger.getSourcePlanId() == null) {
            throw BizException.of(ErrorCode.PLAN_NOT_LINKED,
                    "该餐账并非由选餐方案转出，无预算基线可对照");
        }
        if (ledger.getExpectedTotalCents() == null) {
            throw BizException.of(ErrorCode.PLAN_BASELINE_UNAVAILABLE,
                    "该方案尚无官方核价基线，请先在选餐页完成核价");
        }

        LedgerDetailVO detail = ledgerService.getLedger(ledgerId);
        ReconciliationReport recon = detail.getReconciliation();
        long actual = recon == null || recon.getOrdersTotalCents() == null
                ? 0L : recon.getOrdersTotalCents();
        long expected = ledger.getExpectedTotalCents();

        PlanVsActualVO vo = new PlanVsActualVO();
        vo.setLedgerId(ledgerId);
        vo.setPlanId(ledger.getSourcePlanId());
        vo.setExpectedTotalCents(expected);
        vo.setActualTotalCents(actual);
        long diff = actual - expected;
        vo.setDiffCents(diff);
        vo.setDirection(diff > 0 ? "OVER" : (diff < 0 ? "UNDER" : "EQUAL"));

        PlanSnapshot snap = pricingService.latestRow(ledger.getSourcePlanId());
        if (snap != null) {
            vo.setBaselineConfidence(snap.getConfidence());
            vo.setBaselineStale(Boolean.TRUE.equals(snap.getPartial()) ? null : Boolean.FALSE);
        }

        // 人员级：预期按 displayName 对齐（转出时人员 1:1，名称唯一）
        Map<String, Long> expectedByPerson = expectedByPerson(snap);
        Map<Long, Long> actualByPerson = new HashMap<>();
        if (recon != null && recon.getPersons() != null) {
            for (PersonRecon pr : recon.getPersons()) {
                actualByPerson.put(pr.getParticipantId(),
                        pr.getTotalCents() == null ? 0L : pr.getTotalCents());
            }
        }
        long personDiffSum = 0L;
        for (ParticipantVO p : detail.getParticipants()) {
            PlanVsActualVO.PersonDiff pd = new PlanVsActualVO.PersonDiff();
            pd.setParticipantId(p.getId());
            pd.setDisplayName(p.getDisplayName());
            long e = expectedByPerson.getOrDefault(p.getDisplayName(), 0L);
            long a = actualByPerson.getOrDefault(p.getId(), 0L);
            pd.setExpectedCents(e);
            pd.setActualCents(a);
            pd.setDiffCents(a - e);
            personDiffSum += (a - e);
            vo.getPersons().add(pd);
        }
        vo.setBalanced(personDiffSum == diff);
        return vo;
    }

    private Map<String, Long> expectedByPerson(PlanSnapshot snap) {
        Map<String, Long> map = new HashMap<>();
        if (snap == null || snap.getPerPersonExpectedJson() == null) {
            return map;
        }
        try {
            SnapshotVO.PerPersonExpected[] arr = mapper.readValue(snap.getPerPersonExpectedJson(),
                    SnapshotVO.PerPersonExpected[].class);
            for (SnapshotVO.PerPersonExpected e : arr) {
                if (e.getDisplayName() != null && e.getExpectedCents() != null) {
                    map.put(e.getDisplayName(), e.getExpectedCents());
                }
            }
        } catch (Exception ignore) {
            // 基线 JSON 异常不阻断差异对照（退化为总额级）
        }
        return map;
    }

    /** 供前端做「合计行」校验：人员级差异之和。 */
    public long personDiffSum(List<PlanVsActualVO.PersonDiff> persons) {
        long sum = 0L;
        for (PlanVsActualVO.PersonDiff p : persons) {
            sum += p.getDiffCents() == null ? 0L : p.getDiffCents();
        }
        return sum;
    }
}
