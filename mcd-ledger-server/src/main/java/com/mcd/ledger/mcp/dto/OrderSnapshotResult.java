package com.mcd.ledger.mcp.dto;

import com.mcd.ledger.domain.LedgerOrder;
import com.mcd.ledger.domain.OrderItem;
import java.util.List;
import lombok.Data;

/** query-order 深度明细转换结果：订单快照 + 餐品项 + 置信度。 */
@Data
public class OrderSnapshotResult {
    private LedgerOrder order;
    private List<OrderItem> items;
}
