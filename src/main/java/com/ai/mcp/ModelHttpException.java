package com.ai.mcp;

/**
 * 模型 HTTP 端点调用异常，携带响应状态码和是否允许重试的判定。
 *
 * @author data-agent
 */
public class ModelHttpException extends IllegalStateException {

    private final int statusCode;
    private final boolean retriable;

    public ModelHttpException(int statusCode, String message, boolean retriable) {
        super(message);
        this.statusCode = statusCode;
        this.retriable = retriable;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public boolean isRetriable() {
        return retriable;
    }
}
