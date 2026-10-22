package com.mcd.ledger.web;

import com.mcd.ledger.dto.AddOrdersRequest;
import com.mcd.ledger.dto.OrderVO;
import com.mcd.ledger.mcp.OrderAdapter;
import com.mcd.ledger.mcp.dto.OrderCard;
import com.mcd.ledger.service.LedgerService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** MCP 订单代理 + 选入订单（设计 §6）。 */
@RestController
public class McpOrderController {

    private final LedgerService service;
    private final OrderAdapter orderAdapter;

    public McpOrderController(LedgerService service, OrderAdapter orderAdapter) {
        this.service = service;
        this.orderAdapter = orderAdapter;
    }

    /** 代理 order-list，返回脱敏可选订单卡片。 */
    @GetMapping("/api/v1/mcp/orders")
    public List<OrderCard> orders() {
        return orderAdapter.listOrders();
    }

    /** 选入订单：服务端调 query-order 生成快照；重复 mcpOrderId 幂等跳过、自动过滤取消单。 */
    @PostMapping("/api/v1/ledgers/{id}/orders")
    public List<OrderVO> addOrders(@PathVariable Long id, @RequestBody @Valid AddOrdersRequest req) {
        return service.addOrders(id, req.getMcpOrderIds());
    }
}
