package com.mcd.plan.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 单成员选餐 + 小计（组队选餐汇总）。金额单位：分。 */
@Data
public class ParticipantSelectionVO {
    private Long participantId;
    private String displayName;
    private List<SelectionItemVO> items = new ArrayList<>();
    private Long subtotalCents;
}
