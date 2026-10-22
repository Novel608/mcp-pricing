package com.mcd.ledger.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 餐品项（API 契约 orders[].items[]）。 */
@Data
public class ItemVO {
    private Long id;
    private String externalItemId;
    private String name;
    private Integer quantity;
    private Long itemTotalCents;
    private String allocationType;
    private String confidence;
    private List<AllocVO> allocations = new ArrayList<>();
}
