package com.ai.vector;

/**
 * 表示 Embedding 调用被当前 Profile 的熔断器拒绝，未触达外部服务。
 *
 * @author data-agent
 */
public class EmbeddingCallRejectedException extends IllegalStateException {

    /**
     * 创建不包含输入文本或凭据的熔断拒绝异常。
     *
     * @param profileIdentity Embedding Profile 安全身份
     * @param operation 低基数操作名
     */
    public EmbeddingCallRejectedException(String profileIdentity, String operation) {
        super("Embedding 调用被熔断器拒绝: profile=" + profileIdentity
                + ", operation=" + operation);
    }
}
