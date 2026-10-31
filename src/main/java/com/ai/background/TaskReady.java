package com.ai.background;

/** 提交后通知只包含队列和持久任务 ID，不传用户正文或可伪造的授权快照。 */
public record TaskReady(String queue, String taskId) { }
