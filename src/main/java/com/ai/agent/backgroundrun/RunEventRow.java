package com.ai.agent.backgroundrun;

/** 持久事件的全局游标；同一 Run 的写入由提交行锁串行化。 */
public record RunEventRow(long id, String payload) { }
