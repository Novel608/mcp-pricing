package com.mcd.plan.dto;

import com.mcd.plan.domain.PlanRecommendation;
import java.time.LocalDateTime;
import lombok.Data;

/** 推荐出参（设计 §3.4 #7/#8）：含 R6 三元组与 notWorthIt 灰显标记。 */
@Data
public class RecommendationVO {

    private Long id;
    private String type;
    private String title;
    private String reasonJson;
    private Long discountCents;
    private Long addedCostCents;
    /** COUPON 为 NULL（R3 不给金额） */
    private Long netSavingCents;
    private String priceSource;
    private Boolean accepted;
    /** netSaving ≤ 0 → true，前端灰显不置顶 */
    private Boolean notWorthIt;
    private LocalDateTime createdAt;

    public static RecommendationVO from(PlanRecommendation r) {
        RecommendationVO v = new RecommendationVO();
        v.setId(r.getId());
        v.setType(r.getType());
        v.setTitle(r.getTitle());
        v.setReasonJson(r.getReasonJson());
        v.setDiscountCents(r.getDiscountCents());
        v.setAddedCostCents(r.getAddedCostCents());
        v.setNetSavingCents(r.getNetSavingCents());
        v.setPriceSource(r.getPriceSource());
        v.setAccepted(r.getAccepted());
        v.setNotWorthIt(r.getNotWorthIt());
        v.setCreatedAt(r.getCreatedAt());
        return v;
    }
}
