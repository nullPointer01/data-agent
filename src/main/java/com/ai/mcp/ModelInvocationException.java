package com.ai.mcp;

/** 模型调用失败的明确边界；安全说明交给 Run 错误通道，不作为正常模型回答保存。 */
public class ModelInvocationException extends RuntimeException {

    /** 创建可安全向用户展示的调用异常。 */
    public ModelInvocationException(String message) {
        super(message);
    }

    /** 保留底层异常供排查，用户仅接收安全说明。 */
    public ModelInvocationException(String message, Throwable cause) {
        super(message, cause);
    }
}
