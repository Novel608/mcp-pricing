package com.mcd.ledger.common;

import com.mcd.ledger.domain.AuditLog;
import com.mcd.ledger.mapper.AuditLogMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 审计日志（设计 §7.5）：单行 JSON {"ts","action","target","result","actor","detailJson"}。
 * 写库 + SLF4J 双通道；写库失败不影响主流程（仅告警）。
 */
@Component
public class AuditLogger {

    private static final Logger log = LoggerFactory.getLogger(AuditLogger.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AuditLogMapper mapper;

    public AuditLogger(AuditLogMapper mapper) {
        this.mapper = mapper;
    }

    /** 写工具拦截固定 action=MCP_TOOL_DENIED, result=DENIED */
    public void toolDenied(String tool) {
        record(null, "MCP_TOOL_DENIED", tool, null, "DENIED", "system");
    }

    public void record(Long ledgerId, String action, String target,
                       String beforeJson, String afterJson, String operator) {
        String line = String.format(
                "{\"ts\":\"%s\",\"action\":\"%s\",\"target\":\"%s\",\"result\":\"OK\",\"actor\":\"%s\"}",
                LocalDateTime.now(), action, target, operator);
        log.info("AUDIT {}", line);
        try {
            AuditLog a = new AuditLog();
            a.setLedgerId(ledgerId);
            a.setAction(action);
            a.setTarget(target);
            a.setBeforeJson(beforeJson);
            a.setAfterJson(afterJson);
            a.setOperator(operator);
            a.setCreatedAt(LocalDateTime.now());
            mapper.insert(a);
        } catch (Exception e) {
            log.warn("audit persist failed for action={} target={}: {}", action, target, e.getMessage());
        }
    }
}
