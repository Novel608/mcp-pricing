package com.mcd.plan.web;

import com.mcd.plan.dto.AcceptRecommendationRequest;
import com.mcd.plan.dto.AddParticipantRequest;
import com.mcd.plan.dto.CreatePlanRequest;
import com.mcd.plan.dto.PlanDetailVO;
import com.mcd.plan.dto.PricingRequest;
import com.mcd.plan.dto.RecommendationVO;
import com.mcd.plan.dto.SnapshotVO;
import com.mcd.plan.dto.SubmitSelectionRequest;
import com.mcd.plan.engine.PlanPricingService;
import com.mcd.plan.service.MealPlanService;
import com.mcd.plan.service.RecommendationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 选餐域 REST（/api/v1/plans，设计 §3.4）。
 * T03：建方案/成员/选餐/汇总；T04：推荐（#7/#8）+ 官方核价基线（#9/#10）。
 */
@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

    private final MealPlanService service;
    private final RecommendationService recommendationService;
    private final PlanPricingService pricingService;

    public PlanController(MealPlanService service, RecommendationService recommendationService,
                          PlanPricingService pricingService) {
        this.service = service;
        this.recommendationService = recommendationService;
        this.pricingService = pricingService;
    }

    @PostMapping
    public Long create(@RequestBody @Valid CreatePlanRequest req) {
        return service.createPlan(req);
    }

    /** 主读接口：成员 / 选餐 / 汇总 / 推荐 / 基线（设计 §3.4 #2）。 */
    @GetMapping("/{id}")
    public PlanDetailVO get(@PathVariable Long id) {
        PlanDetailVO vo = service.getPlan(id);
        vo.setRecommendations(recommendationService.list(id));
        vo.setSnapshot(pricingService.latestOrNull(id));
        return vo;
    }

    @PostMapping("/{id}/participants")
    public Long addParticipant(@PathVariable Long id, @RequestBody @Valid AddParticipantRequest req) {
        return service.addParticipant(id, req);
    }

    @PutMapping("/{id}/selections")
    public Map<String, Object> submitSelection(@PathVariable Long id,
                                               @RequestBody @Valid SubmitSelectionRequest req) {
        service.submitSelection(id, req);
        return Map.of("ok", true);
    }

    /** #7：触发 R1/R2/R3 推荐计算，结果落 plan_recommendation。 */
    @PostMapping("/{id}/recommendations")
    public List<RecommendationVO> recommend(@PathVariable Long id) {
        return recommendationService.recommend(id);
    }

    /** #8：采纳 / 取消采纳。 */
    @PatchMapping("/{id}/recommendations/{recId}")
    public Map<String, Object> accept(@PathVariable Long id, @PathVariable Long recId,
                                      @RequestBody @Valid AcceptRecommendationRequest req) {
        recommendationService.accept(id, recId, Boolean.TRUE.equals(req.getAccepted()));
        return Map.of("ok", true);
    }

    /** #9：触发官方 calculate-price 生成 PlanSnapshot。 */
    @PostMapping("/{id}/pricing")
    public SnapshotVO pricing(@PathVariable Long id, @RequestBody(required = false) PricingRequest req) {
        String orderType = req == null ? null : req.getOrderType();
        String beType = req == null ? null : req.getBeType();
        return pricingService.price(id, orderType, beType);
    }

    /** #10：读取最新预期基线（含 stale 软提示）。 */
    @GetMapping("/{id}/snapshot")
    public SnapshotVO snapshot(@PathVariable Long id) {
        return pricingService.latest(id);
    }
}
