package com.mcd.plan.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 采纳 / 取消采纳推荐（设计 §3.4 #8）。 */
@Data
public class AcceptRecommendationRequest {

    @NotNull(message = "accepted 必填")
    private Boolean accepted;
}
