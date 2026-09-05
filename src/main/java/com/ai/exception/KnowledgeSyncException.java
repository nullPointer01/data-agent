package com.ai.exception;

/**
 * 外部知识同步失败时抛出的异常。
 *
 * @author data-agent
 */
public class KnowledgeSyncException extends RuntimeException {

    public KnowledgeSyncException(String message) {
        super(message);
    }

    public KnowledgeSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
