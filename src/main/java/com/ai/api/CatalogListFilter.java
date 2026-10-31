package com.ai.api;

/** 配置清单查询条件；租户和归属只由服务端身份填入，不从请求绑定。 */
public record CatalogListFilter(String tenantId, String ownerId, boolean includeShared,
        boolean enabledOnly, Boolean defaultAgent, String query) {
    public CatalogListFilter {
        if (tenantId == null || tenantId.isBlank()) throw new IllegalArgumentException("缺少租户身份");
        query = ListPaging.keyword(query);
    }
}
