package com.ai.token;

/** 实际厂商用量、完整响应的本地估算、或无法确认用量时的保守扣额；历史记录单独标记。 */
public enum TokenAccountingStatus {
    ACTUAL, ESTIMATED, UNKNOWN, LEGACY
}
