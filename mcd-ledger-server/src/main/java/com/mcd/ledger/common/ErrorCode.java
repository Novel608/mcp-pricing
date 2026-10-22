package com.mcd.ledger.common;

/**
 * 错误码规范（设计 §7.2，仅对账主线所用子集）。
 * HTTP 状态码与 code 一一映射，由 GlobalExceptionHandler 落地。
 */
public enum ErrorCode {

    OK("OK", 200),
    PARAM_ERROR("PARAM_ERROR", 400),
    MCP_AUTH_FAILED("MCP_AUTH_FAILED", 401),
    MCP_WRITE_TOOL_DENIED("MCP_WRITE_TOOL_DENIED", 403),
    PLAN_NOT_FOUND("PLAN_NOT_FOUND", 404),
    PLAN_NOT_LINKED("PLAN_NOT_LINKED", 404),
    PLAN_BASELINE_UNAVAILABLE("PLAN_BASELINE_UNAVAILABLE", 404),
    LEDGER_NOT_FOUND("LEDGER_NOT_FOUND", 404),
    LEDGER_DIFF_NOT_ZERO("LEDGER_DIFF_NOT_ZERO", 409),
    REASON_REQUIRED("REASON_REQUIRED", 400),
    MCP_RATE_LIMITED("MCP_RATE_LIMITED", 429),
    PRICE_UNAVAILABLE("PRICE_UNAVAILABLE", 502),
    MCP_UPSTREAM_ERROR("MCP_UPSTREAM_ERROR", 502),
    ENGINE_IDENTITY_BROKEN("ENGINE_IDENTITY_BROKEN", 500),
    INTERNAL_ERROR("INTERNAL_ERROR", 500);

    private final String code;
    private final int httpStatus;

    ErrorCode(String code, int httpStatus) {
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String code() { return code; }
    public int httpStatus() { return httpStatus; }
}
