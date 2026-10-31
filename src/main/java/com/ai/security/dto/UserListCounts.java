package com.ai.security.dto;

/** 与用户搜索条件一致的数据库聚合，不用当前页冒充账号总量。 */
public record UserListCounts(long total, long enabledCount, long adminCount) { }
