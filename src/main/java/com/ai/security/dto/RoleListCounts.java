package com.ai.security.dto;

/** 角色搜索结果的数据库聚合，指标不随翻页变化。 */
public record RoleListCounts(long total, long enabledCount, long systemCount) { }
