package com.mcd.plan.engine;

import com.mcd.plan.domain.PlanRecommendation;
import java.util.List;

/** 推荐引擎（设计 §3.2）：贪心枚举 R1/R2/R3，输出按 netSavingCents 降序。 */
public interface RecommendationEngine {

    /**
     * 入参不可变；返回按 netSavingCents 降序（NULL 排最后），≤0 的项已打 notWorthIt 标记但不剔除。
     */
    List<PlanRecommendation> recommend(PlanContext ctx);
}
