package com.ai.config;

import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;

/**
 * 全局共享的 TokenCountEstimator 单例。
 *
 * <p>{@link OpenAiTokenCountEstimator} 构造时会把 tiktoken BPE 词表加载进内存，开销较大。
 * 多处各自 {@code new} 会重复加载词表、浪费内存与初始化时间，因此集中为一个不可变单例复用。
 * TokenCountEstimator 实现是无状态、线程安全的，可被所有调用方共享。</p>
 *
 * @author data-agent
 */
public final class SharedTokenizer {

    /** 共享的 gpt-4 编码 TokenCountEstimator，线程安全、可被所有调用方复用。 */
    public static final TokenCountEstimator INSTANCE = new OpenAiTokenCountEstimator("gpt-4");

    private SharedTokenizer() {
    }
}
