package com.mcd.plan.web;

import com.mcd.plan.dto.PlanDetailVO;
import com.mcd.plan.engine.PlanPricingService;
import com.mcd.plan.service.MealPlanService;
import com.mcd.plan.service.RecommendationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 沙盒选餐方案端点（设计 §3.4 #13）：无需 Token，直接返回沙盒方案（id=1）。
 * 与真实链路共用 MealPlanService / 推荐引擎 / 核价出口；推荐走**只读预演**（不落库），
 * 保证 GET 演示端点无写副作用。
 */
@RestController
public class DemoPlanController {

    private final MealPlanService service;
    private final RecommendationService recommendationService;
    private final PlanPricingService pricingService;

    public DemoPlanController(MealPlanService service, RecommendationService recommendationService,
                              PlanPricingService pricingService) {
        this.service = service;
        this.recommendationService = recommendationService;
        this.pricingService = pricingService;
    }

    @GetMapping("/api/v1/demo/plan")
    public PlanDetailVO demo() {
        PlanDetailVO vo = service.getPlan(1L);
        vo.setRecommendations(recommendationService.preview(1L));
        vo.setSnapshot(pricingService.latestOrNull(1L));
        return vo;
    }
}
