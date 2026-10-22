package com.mcd.ledger.dto;

import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 设置餐品归属请求：PERSONAL（单人）/ SHARED（多人+权重）/ 空列表 = UNASSIGNED。 */
@Data
public class SetAllocationRequest {

    @Valid
    private List<AllocSpec> allocations = new ArrayList<>();

    @Data
    public static class AllocSpec {
        private Long participantId;
        private Integer weight;
    }
}
