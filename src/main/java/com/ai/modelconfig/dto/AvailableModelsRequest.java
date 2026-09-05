package com.ai.modelconfig.dto;

/**
 * 拉取厂商可用模型列表的请求。
 *
 * <p>编辑已保存的模型时 apiKey 通常是掩码，此时传 modelId，
 * 服务端会从数据库读取真实密钥。</p>
 *
 * @param modelId 已保存的模型编号，可为空
 * @param provider 厂商标识
 * @param apiKey API 密钥，可为掩码
 * @param baseUrl 接口地址，可为空（用厂商默认值）
 * @author data-agent
 */
public record AvailableModelsRequest(
        String modelId,
        String provider,
        String apiKey,
        String baseUrl) {
}
