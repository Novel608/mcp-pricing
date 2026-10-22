package com.mcd.ledger.common;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 成功响应统一包装为 ApiResponse（设计 §7.3）。
 * 跳过：已是 ApiResponse、ResponseEntity（如 CSV 导出，保留原始 text/csv 与状态码）。
 */
@RestControllerAdvice
public class ResponseWrapperAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType, Class converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ApiResponse || body instanceof ResponseEntity) {
            return body;
        }
        // CSV / 非 JSON 导出不包装
        if (selectedContentType != null && !selectedContentType.includes(MediaType.APPLICATION_JSON)) {
            return body;
        }
        return ApiResponse.ok(body);
    }
}
