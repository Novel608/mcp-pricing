package com.mcd.plan.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.mcd.ledger.common.BizException;
import com.mcd.ledger.common.ErrorCode;
import com.mcd.plan.domain.enums.RecommendType;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 推荐留痕（设计 §3.5 plan_recommendation，R6 契约）。
 * COMBO/THRESHOLD：discountCents/addedCostCents/netSavingCents 必填且 netSaving=discount-addedCost；
 * COUPON：netSavingCents 为 NULL（券面额官方但适用性不可验证）。
 */
@Data
@TableName("plan_recommendation")
public class PlanRecommendation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long planId;

    /** COMBO / THRESHOLD / COUPON */
    private String type;

    private String title;

    private String reasonJson;

    private Long discountCents;

    private Long addedCostCents;

    /** COUPON 类型为 NULL */
    private Long netSavingCents;

    /** R6：netSavingCents ≤ 0（或不可核算）→ 引擎自动标记，前端灰显不置顶（设计 T-3） */
    private Boolean notWorthIt;

    /** MCP_CALCULATED / MCP_REFERENCE（R6 三元组必填） */
    private String priceSource;

    private Boolean accepted;

    private LocalDateTime createdAt;

    /**
     * R6 契约校验（设计 §3.2）：净节省按推荐类型分级，任何持久化/出参边界都必须先过此方法。
     * COMBO/THRESHOLD：三元组必填且 netSaving = discount − addedCost；COUPON：netSaving 必须 NULL。
     */
    public void validateR6() {
        if (priceSource == null) {
            throw BizException.of(ErrorCode.PARAM_ERROR, "R6：priceSource 必填（MCP_CALCULATED / MCP_REFERENCE）");
        }
        if (reasonJson == null || reasonJson.isBlank()) {
            throw BizException.of(ErrorCode.REASON_REQUIRED, "R6：reasonJson 必填");
        }
        if (RecommendType.COMBO.name().equals(type) || RecommendType.THRESHOLD.name().equals(type)) {
            if (discountCents == null || addedCostCents == null || netSavingCents == null) {
                throw BizException.of(ErrorCode.PARAM_ERROR,
                        "R6：" + type + " 的 discount/addedCost/netSaving 三元组必填");
            }
            if (discountCents - addedCostCents != netSavingCents) {
                throw BizException.of(ErrorCode.PARAM_ERROR,
                        "R6：" + type + " 的 netSaving 必须等于 discount − addedCost");
            }
        } else if (RecommendType.COUPON.name().equals(type)) {
            if (netSavingCents != null) {
                throw BizException.of(ErrorCode.PARAM_ERROR,
                        "R6：COUPON 券匹配不提供净节省金额，netSavingCents 必须为 NULL");
            }
        }
    }
}
