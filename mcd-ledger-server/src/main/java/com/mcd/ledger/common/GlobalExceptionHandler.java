package com.mcd.ledger.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 按 ErrorCode 映射 HTTP 状态码（设计 §7.2）。
 * 业务失败返回 ApiResponse.error，HTTP 状态与 code 一一对应。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBiz(BizException ex, HttpServletRequest req) {
        ErrorCode code = ex.getErrorCode();
        if (code.httpStatus() >= 500) {
            log.error("biz error {} at {}: {}", code.code(), req.getRequestURI(), ex.getMessage());
        } else {
            log.warn("biz error {} at {}: {}", code.code(), req.getRequestURI(), ex.getMessage());
        }
        return ResponseEntity.status(code.httpStatus())
                .body(ApiResponse.error(code, ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + " " + e.getDefaultMessage())
                .orElse("参数校验失败");
        return ResponseEntity.status(ErrorCode.PARAM_ERROR.httpStatus())
                .body(ApiResponse.error(ErrorCode.PARAM_ERROR, msg));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArg(IllegalArgumentException ex) {
        return ResponseEntity.status(ErrorCode.PARAM_ERROR.httpStatus())
                .body(ApiResponse.error(ErrorCode.PARAM_ERROR, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex, HttpServletRequest req) {
        log.error("unexpected error at {}", req.getRequestURI(), ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.httpStatus())
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR, "服务内部错误"));
    }
}
