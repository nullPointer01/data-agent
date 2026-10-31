package com.ai.resource;

import com.ai.api.ListPageResponse;
import com.ai.api.ListPaging;
import com.ai.resource.dto.ResourceAssetResponse;
import com.ai.resource.dto.ResourceSummaryResponse;
import com.ai.security.SecurityContextHelper;
import com.ai.service.VectorMemoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

/** 资源中心统一列表和全量汇总；数据库查询保留原有当前租户可见范围。 */
@Service
public class ResourceCenterService {
    private final ResourceAssetMapper mapper;
    private final SecurityContextHelper security;
    private final VectorMemoryService vectors;

    public ResourceCenterService(ResourceAssetMapper mapper, SecurityContextHelper security,
            VectorMemoryService vectors) {
        this.mapper = mapper;
        this.security = security;
        this.vectors = vectors;
    }

    @Transactional(readOnly = true)
    public ListPageResponse<ResourceAssetResponse> listAssets(String keyword, String resourceType,
            String status, int page, int size) {
        ListPaging.page(page, size);
        String tenant = security.getCurrentTenantId();
        String query = ListPaging.keyword(keyword);
        String type = ListPaging.keyword(resourceType).toUpperCase(Locale.ROOT);
        String state = ListPaging.keyword(status).toUpperCase(Locale.ROOT);
        if (!type.isEmpty() && !type.equals("FILE") && !type.equals("KNOWLEDGE")) {
            throw new IllegalArgumentException("资源类型无效");
        }
        return ListPageResponse.fetch(page, size, mapper.count(tenant, query, type, state),
                pageable -> mapper.list(tenant, query, type, state, pageable));
    }

    public ResourceSummaryResponse getSummary() {
        ResourceSummaryResponse summary = mapper.summary(security.getCurrentTenantId());
        summary.setUsingMilvus(vectors.isUsingMilvus());
        return summary;
    }
}
