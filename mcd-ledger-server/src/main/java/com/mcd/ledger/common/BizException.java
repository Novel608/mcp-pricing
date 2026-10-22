package com.mcd.ledger.common;

/**
 * 业务异常基类。携带 ErrorCode 与可读 message。
 */
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    private BizException(ErrorCode errorCode, String message) {
        super(message != null ? message : errorCode.code());
        this.errorCode = errorCode;
    }

    public static BizException of(ErrorCode errorCode) {
        return new BizException(errorCode, errorCode.code());
    }

    public static BizException of(ErrorCode errorCode, String message) {
        return new BizException(errorCode, message);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
