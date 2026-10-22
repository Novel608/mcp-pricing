package com.mcd.ledger.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 选入订单请求（服务端调 query-order 生成快照；重复 mcpOrderId 幂等跳过）。 */
@Data
public class AddOrdersRequest {

    @NotEmpty(message = "mcpOrderIds 不能为空")
    private List<String> mcpOrderIds = new ArrayList<>();
}
