package com.mcd.plan.service;

import com.mcd.ledger.domain.MealLedger;
import com.mcd.ledger.dto.CreateLedgerRequest;
import com.mcd.ledger.mapper.MealLedgerMapper;
import com.mcd.ledger.service.LedgerService;
import com.mcd.plan.domain.MealPlan;
import com.mcd.plan.domain.PlanSnapshot;
import com.mcd.plan.dto.PlanDetailVO;
import com.mcd.plan.dto.PlanParticipantVO;
import com.mcd.plan.engine.PlanPricingService;
import com.mcd.plan.mapper.MealPlanMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 方案 → 餐账转出（设计 §3.4 #11，M7）：**唯一正式入口**。
 *
 * <p>复用 ledger 域既有 `createInternal(spec)` 建账（人员 1:1、项目按方案侧绑定预填），
 * 随后由 plan 域写入可空外键 `meal_ledger.source_plan_id` 与预期总额
 * `expected_total_cents`（DR-2：ledger 域既有逻辑永远不读这两列）。
 * 无官方核价基线时 `expectedTotalCents` 留空 —— `/vs-plan` 会按 C3 返回 PLAN_BASELINE_UNAVAILABLE。
 */
@Service
public class PlanToLedgerConverter {

    private final MealPlanService planService;
    private final LedgerService ledgerService;
    private final MealLedgerMapper ledgerMapper;
    private final MealPlanMapper planMapper;
    private final PlanPricingService pricingService;

    public PlanToLedgerConverter(MealPlanService planService, LedgerService ledgerService,
                                 MealLedgerMapper ledgerMapper, MealPlanMapper planMapper,
                                 PlanPricingService pricingService) {
        this.planService = planService;
        this.ledgerService = ledgerService;
        this.ledgerMapper = ledgerMapper;
        this.planMapper = planMapper;
        this.pricingService = pricingService;
    }

    /** 转出：返回新建餐账 id。重复转出不阻断（每次生成一本新账）。 */
    public Long convert(Long planId) {
        PlanDetailVO vo = planService.getPlan(planId);

        CreateLedgerRequest req = new CreateLedgerRequest();
        req.setTitle(vo.getTitle());
        req.setMealTime(vo.getMealTime());
        req.setOperatorName("组队选餐");

        // 项目：按方案侧参与人绑定的 projectId 去重预填（名称沿用编号，后续可在账内改名）
        Map<Long, CreateLedgerRequest.ProjectSpec> projects = new LinkedHashMap<>();
        for (PlanParticipantVO p : vo.getParticipants()) {
            if (p.getProjectId() == null) {
                continue;
            }
            projects.computeIfAbsent(p.getProjectId(), id -> {
                CreateLedgerRequest.ProjectSpec ps = new CreateLedgerRequest.ProjectSpec();
                ps.setName("项目 " + id);
                ps.setCode("PLAN-PROJ-" + id);
                return ps;
            });
        }
        req.getProjects().addAll(projects.values());

        // 人员 1:1 迁移（含 personRef / projectId / sortOrder）
        for (PlanParticipantVO p : vo.getParticipants()) {
            CreateLedgerRequest.ParticipantSpec ps = new CreateLedgerRequest.ParticipantSpec();
            ps.setDisplayName(p.getDisplayName());
            ps.setPersonRef(p.getPersonRef());
            ps.setProjectId(p.getProjectId());
            ps.setSortOrder(p.getSortOrder());
            req.getParticipants().add(ps);
        }

        Long ledgerId = ledgerService.createInternal(req);

        // 回写来源与预期基线（plan 域唯一写入点）
        MealLedger ledger = ledgerMapper.selectById(ledgerId);
        if (ledger != null) {
            ledger.setSourcePlanId(planId);
            PlanSnapshot snap = pricingService.latestRow(planId);
            ledger.setExpectedTotalCents(snap == null ? null : snap.getExpectedTotalCents());
            ledgerMapper.updateById(ledger);
        }

        MealPlan plan = planMapper.selectById(planId);
        if (plan != null) {
            plan.setStatus("CONVERTED");
            planMapper.updateById(plan);
        }
        return ledgerId;
    }
}
