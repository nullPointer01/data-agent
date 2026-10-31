package com.ai.memory;

/**
 * 规划器对本轮长期记忆提取的调度建议；候选仍须通过提取器的证据与准入校验。
 */
public enum MemoryCaptureMode {
    /** 普通问答、回忆提问或临时要求，不调用记忆提取模型。 */
    SKIP,
    /** 本轮包含新陈述的稳定个人事实或偏好，回答保存后后台提取。 */
    BACKGROUND
}
