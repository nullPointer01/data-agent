package com.ai.token;

/** 本地账务不可确认时停止调用，不能作为厂商网络错误重试付费请求。 */
public class TokenAccountingException extends RuntimeException {
    public TokenAccountingException(String message, Throwable cause) { super(message, cause); }
}
