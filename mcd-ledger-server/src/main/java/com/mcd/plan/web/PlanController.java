package com.mcd.plan.web;

import com.mcd.plan.dto.AddParticipantRequest;
import com.mcd.plan.dto.CreatePlanRequest;
import com.mcd.plan.dto.PlanDetailVO;
import com.mcd.plan.dto.SubmitSelectionRequest;
import com.mcd.plan.service.MealPlanService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 选餐域 REST（/api/v1/plans，设计 §3.4）。T03：建方案/成员/选餐/汇总。 */
@RestController
@RequestMapping("/api/v1/plans")
public class PlanController {

    private final MealPlanService service;

    public PlanController(MealPlanService service) {
        this.service = service;
    }

    @PostMapping
    public Long create(@RequestBody @Valid CreatePlanRequest req) {
        return service.createPlan(req);
    }

    @GetMapping("/{id}")
    public PlanDetailVO get(@PathVariable Long id) {
        return service.getPlan(id);
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
}
