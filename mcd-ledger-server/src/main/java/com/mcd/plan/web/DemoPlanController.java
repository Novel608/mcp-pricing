package com.mcd.plan.web;

import com.mcd.plan.dto.PlanDetailVO;
import com.mcd.plan.service.MealPlanService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 沙盒选餐方案端点（设计 §3.4 #13）：无需 Token，直接返回持久化的沙盒方案（id=1）。
 * 与真实链路共用 MealPlanService 与汇总出口。
 */
@RestController
public class DemoPlanController {

    private final MealPlanService service;

    public DemoPlanController(MealPlanService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/demo/plan")
    public PlanDetailVO demo() {
        return service.getPlan(1L);
    }
}
