package com.ai.resource;

import com.ai.api.ListPageResponse;
import com.ai.resource.dto.ResourceAssetResponse;
import com.ai.resource.dto.ResourceSummaryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资源中心 API。
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/resources")
public class ResourceCenterController {

    private final ResourceCenterService resourceCenterService;

    public ResourceCenterController(ResourceCenterService resourceCenterService) {
        this.resourceCenterService = resourceCenterService;
    }

    /**
     * 查询当前租户的文件和知识资源，可按关键字、类型及状态过滤。
     *
     * @param keyword 可选的搜索关键字
     * @param resourceType 可选的资源类型
     * @param status 可选的处理状态
     * @return 聚合后的资源列表
     */
    @GetMapping("/assets")
    public ListPageResponse<ResourceAssetResponse> listAssets(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "resourceType", required = false) String resourceType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return resourceCenterService.listAssets(keyword, resourceType, status, page, size);
    }

    /**
     * 汇总当前租户的资源数量、索引块和存储占用。
     *
     * @return 资源中心统计
     */
    @GetMapping("/summary")
    public ResourceSummaryResponse getSummary() {
        return resourceCenterService.getSummary();
    }
}
