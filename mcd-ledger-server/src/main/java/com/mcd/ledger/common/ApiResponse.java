package com.mcd.ledger.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;

/**
 * 统一响应包装（设计 §7.3）：
 * { "code":"OK", "message":null, "traceId":"a1b2c3", "timestamp":"...", "data":{...} }
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final String code;
    private final String message;
    private final String traceId;
    private final String timestamp;
    private final T data;

    private ApiResponse(String code, String message, String traceId, T data) {
        this.code = code;
        this.message = message;
        this.traceId = traceId;
        this.timestamp = OffsetDateTime.now().toString();
        this.data = data;
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(ErrorCode.OK.code(), null, TraceHolder.id(), data);
    }

    public static <T> ApiResponse<T> error(ErrorCode code, String message) {
        return new ApiResponse<>(code.code(), message, TraceHolder.id(), null);
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public String getTraceId() { return traceId; }
    public String getTimestamp() { return timestamp; }
    public T getData() { return data; }
}
