package com.ai.resource;

import com.ai.resource.dto.ResourceAssetResponse;
import com.ai.resource.dto.ResourceSummaryResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.data.domain.Pageable;

import java.util.List;

/** 资源中心只读投影：文件与知识先在 SQL 合并筛选，再统一排序分页。 */
@Mapper
public interface ResourceAssetMapper {
    List<ResourceAssetResponse> list(@Param("tenantId") String tenantId, @Param("query") String query,
            @Param("type") String type, @Param("status") String status, @Param("pageable") Pageable pageable);
    long count(@Param("tenantId") String tenantId, @Param("query") String query,
            @Param("type") String type, @Param("status") String status);
    ResourceSummaryResponse summary(@Param("tenantId") String tenantId);
}
