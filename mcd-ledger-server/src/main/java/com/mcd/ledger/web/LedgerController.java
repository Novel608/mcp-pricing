package com.mcd.ledger.web;

import com.mcd.ledger.dto.CreateLedgerRequest;
import com.mcd.ledger.dto.LedgerDetailVO;
import com.mcd.ledger.dto.ReconciliationReport;
import com.mcd.ledger.dto.SetAllocationRequest;
import com.mcd.ledger.dto.SetParticipantProjectRequest;
import com.mcd.ledger.dto.SetSubsidyRequest;
import com.mcd.ledger.service.LedgerService;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 对账域 REST（设计 §6）。成功响应由 ResponseWrapperAdvice 统一包装为 ApiResponse。 */
@RestController
@RequestMapping("/api/v1/ledgers")
public class LedgerController {

    private final LedgerService service;

    public LedgerController(LedgerService service) {
        this.service = service;
    }

    @PostMapping
    public Long create(@RequestBody @Valid CreateLedgerRequest req) {
        return service.createInternal(req);
    }

    @GetMapping("/{id}")
    public LedgerDetailVO get(@PathVariable Long id) {
        return service.getLedger(id);
    }

    @GetMapping("/{id}/reconciliation")
    public ReconciliationReport reconciliation(@PathVariable Long id) {
        return service.getReconciliation(id);
    }

    @GetMapping(value = "/{id}/export.csv", produces = "text/csv")
    public ResponseEntity<String> export(@PathVariable Long id) {
        String csv = service.exportCsv(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ledger-" + id + ".csv\"")
                .body(csv);
    }

    @PutMapping("/{id}/subsidy-rule")
    public Map<String, Object> subsidyRule(@PathVariable Long id, @RequestBody SetSubsidyRequest req) {
        service.setSubsidyRule(id, req.getLimitCents(), req.getIncludeDeliveryInSubsidy());
        return Map.of("ok", true);
    }

    @PutMapping("/{id}/participants/{participantId}")
    public Map<String, Object> setParticipantProject(@PathVariable Long id,
                                                     @PathVariable Long participantId,
                                                     @RequestBody SetParticipantProjectRequest req) {
        service.setParticipantProject(id, participantId, req.getProjectId());
        return Map.of("ok", true);
    }

    @PutMapping("/{id}/items/{itemId}/allocation")
    public Map<String, Object> setAllocation(@PathVariable Long id,
                                             @PathVariable Long itemId,
                                             @RequestBody SetAllocationRequest req) {
        service.setAllocation(id, itemId, req);
        return Map.of("ok", true);
    }
}
