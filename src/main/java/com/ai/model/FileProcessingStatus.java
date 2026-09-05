package com.ai.model;

/**
 * 文件处理生命周期状态。
 *
 * @author data-agent
 */
public enum FileProcessingStatus {
    /** 文件等待异步处理。 */
    QUEUED,

    /** 文件正在处理中。 */
    PROCESSING,

    /** 文件处理成功完成。 */
    COMPLETED,

    /** 文件处理失败。 */
    FAILED
}
