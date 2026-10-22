package com.mcd.plan.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 提交当前成员选餐请求：按 (planId, participantId) 整份覆盖（设计 §7.5 幂等）。
 */
@Data
public class SubmitSelectionRequest {

    private Long participantId;

    @Valid
    @NotEmpty(message = "选餐项不能为空")
    private List<SelectionItem> items = new ArrayList<>();

    @Data
    public static class SelectionItem {
        private String externalItemId;
        private String name;
        private Integer quantity;
        /** 单价（分，取自菜单参考价） */
        private Long unitPriceCents;
        /** 规格选项 JSON */
        private String specOptionsJson;
    }
}
