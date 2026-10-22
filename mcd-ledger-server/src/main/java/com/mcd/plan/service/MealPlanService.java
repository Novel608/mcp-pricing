package com.mcd.plan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mcd.ledger.common.BizException;
import com.mcd.ledger.common.ErrorCode;
import com.mcd.plan.domain.MealPlan;
import com.mcd.plan.domain.PlanItemSelection;
import com.mcd.plan.domain.PlanParticipant;
import com.mcd.plan.domain.enums.PlanStatus;
import com.mcd.plan.dto.AddParticipantRequest;
import com.mcd.plan.dto.CreatePlanRequest;
import com.mcd.plan.dto.ParticipantSelectionVO;
import com.mcd.plan.dto.PlanDetailVO;
import com.mcd.plan.dto.PlanParticipantVO;
import com.mcd.plan.dto.PlanSummaryVO;
import com.mcd.plan.dto.SelectionItemVO;
import com.mcd.plan.dto.SubmitSelectionRequest;
import com.mcd.plan.mapper.MealPlanMapper;
import com.mcd.plan.mapper.PlanItemSelectionMapper;
import com.mcd.plan.mapper.PlanParticipantMapper;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 组队选餐方案生命周期：建方案 / 成员 / 选餐（幂等覆盖）/ 汇总。金额单位：分。 */
@Service
public class MealPlanService {

    private final MealPlanMapper planMapper;
    private final PlanParticipantMapper participantMapper;
    private final PlanItemSelectionMapper selectionMapper;

    public MealPlanService(MealPlanMapper planMapper, PlanParticipantMapper participantMapper,
                           PlanItemSelectionMapper selectionMapper) {
        this.planMapper = planMapper;
        this.participantMapper = participantMapper;
        this.selectionMapper = selectionMapper;
    }

    public Long createPlan(CreatePlanRequest req) {
        MealPlan plan = new MealPlan();
        plan.setTitle(req.getTitle());
        plan.setStoreId(req.getStoreId());
        plan.setStoreName(req.getStoreName());
        plan.setChannel(req.getChannel());
        plan.setMealTime(parseTime(req.getMealTime()));
        plan.setStatus(PlanStatus.DRAFT.name());
        plan.setCreatedAt(LocalDateTime.now());
        planMapper.insert(plan);
        Long planId = plan.getId();

        int idx = 0;
        for (CreatePlanRequest.ParticipantSpec ps : req.getParticipants()) {
            PlanParticipant p = new PlanParticipant();
            p.setPlanId(planId);
            p.setDisplayName(ps.getDisplayName());
            p.setPersonRef(ps.getPersonRef());
            p.setProjectId(ps.getProjectId());
            p.setSortOrder(ps.getSortOrder() != null ? ps.getSortOrder() : idx);
            participantMapper.insert(p);
            idx++;
        }
        return planId;
    }

    public Long addParticipant(Long planId, AddParticipantRequest req) {
        requirePlan(planId);
        PlanParticipant p = new PlanParticipant();
        p.setPlanId(planId);
        p.setDisplayName(req.getDisplayName());
        p.setPersonRef(req.getPersonRef());
        p.setProjectId(req.getProjectId());
        p.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : nextSortOrder(planId));
        participantMapper.insert(p);
        return p.getId();
    }

    /** 提交当前成员选餐：按 (planId, participantId) 整份覆盖（幂等）。 */
    public void submitSelection(Long planId, SubmitSelectionRequest req) {
        requirePlan(planId);
        Long participantId = req.getParticipantId();
        // 覆盖前删除该成员旧选餐
        selectionMapper.delete(new LambdaQueryWrapper<PlanItemSelection>()
                .eq(PlanItemSelection::getPlanId, planId)
                .eq(PlanItemSelection::getParticipantId, participantId));
        for (SubmitSelectionRequest.SelectionItem it : req.getItems()) {
            PlanItemSelection s = new PlanItemSelection();
            s.setPlanId(planId);
            s.setParticipantId(participantId);
            s.setExternalItemId(it.getExternalItemId());
            s.setName(it.getName());
            s.setQuantity(it.getQuantity() == null ? 1 : it.getQuantity());
            s.setUnitPriceCents(it.getUnitPriceCents());
            s.setSpecOptionsJson(it.getSpecOptionsJson());
            selectionMapper.insert(s);
        }
    }

    public PlanDetailVO getPlan(Long planId) {
        MealPlan plan = requirePlan(planId);
        List<PlanParticipant> participants = participantMapper.selectList(
                new LambdaQueryWrapper<PlanParticipant>()
                        .eq(PlanParticipant::getPlanId, planId)
                        .orderByAsc(PlanParticipant::getSortOrder)
                        .orderByAsc(PlanParticipant::getId));
        List<PlanItemSelection> selections = selectionMapper.selectList(
                new LambdaQueryWrapper<PlanItemSelection>()
                        .eq(PlanItemSelection::getPlanId, planId)
                        .orderByAsc(PlanItemSelection::getId));

        Map<Long, List<PlanItemSelection>> byParticipant = new LinkedHashMap<>();
        for (PlanItemSelection s : selections) {
            byParticipant.computeIfAbsent(s.getParticipantId(), k -> new ArrayList<>()).add(s);
        }

        PlanDetailVO vo = new PlanDetailVO();
        vo.setId(plan.getId());
        vo.setTitle(plan.getTitle());
        vo.setStoreId(plan.getStoreId());
        vo.setStoreName(plan.getStoreName());
        vo.setChannel(plan.getChannel());
        vo.setMealTime(plan.getMealTime() == null ? null : plan.getMealTime().toString());
        vo.setStatus(plan.getStatus());

        PlanSummaryVO summary = new PlanSummaryVO();
        long total = 0L;
        for (PlanParticipant p : participants) {
            PlanParticipantVO pv = new PlanParticipantVO();
            pv.setId(p.getId());
            pv.setDisplayName(p.getDisplayName());
            pv.setPersonRef(p.getPersonRef());
            pv.setProjectId(p.getProjectId());
            pv.setSortOrder(p.getSortOrder());
            vo.getParticipants().add(pv);

            ParticipantSelectionVO sv = new ParticipantSelectionVO();
            sv.setParticipantId(p.getId());
            sv.setDisplayName(p.getDisplayName());
            long subtotal = 0L;
            for (PlanItemSelection s : byParticipant.getOrDefault(p.getId(), List.of())) {
                SelectionItemVO iv = new SelectionItemVO();
                iv.setExternalItemId(s.getExternalItemId());
                iv.setName(s.getName());
                iv.setQuantity(s.getQuantity());
                iv.setUnitPriceCents(s.getUnitPriceCents());
                iv.setSpecOptionsJson(s.getSpecOptionsJson());
                sv.getItems().add(iv);
                long unit = s.getUnitPriceCents() == null ? 0L : s.getUnitPriceCents();
                subtotal += unit * (s.getQuantity() == null ? 1 : s.getQuantity());
            }
            sv.setSubtotalCents(subtotal);
            vo.getSelections().add(sv);

            PlanSummaryVO.PersonSubtotal ps = new PlanSummaryVO.PersonSubtotal();
            ps.setParticipantId(p.getId());
            ps.setDisplayName(p.getDisplayName());
            ps.setSubtotalCents(subtotal);
            summary.getPerPerson().add(ps);
            total += subtotal;
        }
        summary.setTotalCents(total);
        vo.setSummary(summary);
        return vo;
    }

    private int nextSortOrder(Long planId) {
        Long cnt = participantMapper.selectCount(new LambdaQueryWrapper<PlanParticipant>()
                .eq(PlanParticipant::getPlanId, planId));
        return cnt == null ? 0 : cnt.intValue();
    }

    private MealPlan requirePlan(Long planId) {
        MealPlan plan = planMapper.selectById(planId);
        if (plan == null) {
            throw BizException.of(ErrorCode.PLAN_NOT_FOUND, "选餐方案不存在: " + planId);
        }
        return plan;
    }

    private static LocalDateTime parseTime(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(s);
        } catch (DateTimeParseException e) {
            try {
                return OffsetDateTime.parse(s).toLocalDateTime();
            } catch (DateTimeParseException e2) {
                throw BizException.of(ErrorCode.PARAM_ERROR, "mealTime 格式非法: " + s);
            }
        }
    }
}
