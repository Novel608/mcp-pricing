package com.mcd.plan.engine.rule;

import com.mcd.plan.domain.PlanRecommendation;
import com.mcd.plan.domain.enums.RecommendType;
import com.mcd.plan.engine.PlanContext;
import java.util.List;

/** 单条推荐规则（R1/R2/R3 同构，便于规则链扩展与单测）。 */
public interface RecommendRule {

    RecommendType type();

    List<PlanRecommendation> apply(PlanContext ctx);
}
