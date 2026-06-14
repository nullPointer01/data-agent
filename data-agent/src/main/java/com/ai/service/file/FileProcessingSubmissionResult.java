package com.ai.service.file;

/**
 * 将一个文件提交给异步处理执行器的结果。
 *
 * @param accepted 执行器是否接受了任务
 * @param message 面向用户的提交消息
 * @author data-agent
 */
public record FileProcessingSubmissionResult(boolean accepted, String message) {

    /**
     * 创建接受的提交结果。
     *
     * @return 接受结果
     */
    public static FileProcessingSubmissionResult acceptedResult() {
        return new FileProcessingSubmissionResult(true, null);
    }

    /**
     * 创建拒绝的提交结果。
     *
     * @param message 拒绝原因
     * @return 拒绝结果
     */
    public static FileProcessingSubmissionResult rejectedResult(String message) {
        return new FileProcessingSubmissionResult(false, message);
    }
}
