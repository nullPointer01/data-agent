package com.ai.exception;

/**
 * 当用户超出令牌配额时抛出。
 *
 * @author data-agent
 */
public class QuotaExceededException extends RuntimeException {
    public QuotaExceededException(String message) {
        super(message);
    }
}
