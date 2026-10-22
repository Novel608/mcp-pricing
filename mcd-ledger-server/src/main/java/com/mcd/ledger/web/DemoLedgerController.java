package com.mcd.ledger.web;

import com.mcd.ledger.dto.LedgerDetailVO;
import com.mcd.ledger.service.LedgerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 沙盒演示端点（设计 §6 / §9）：无需 Token，直接返回持久化的沙盒基准餐账（id=1）。
 * 与真实链路共用同一引擎与同一报表结构。
 */
@RestController
public class DemoLedgerController {

    private final LedgerService service;

    public DemoLedgerController(LedgerService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/demo/ledger")
    public LedgerDetailVO demo() {
        return service.getLedger(1L);
    }
}
