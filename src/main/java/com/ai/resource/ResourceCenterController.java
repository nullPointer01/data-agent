package com.ai.resource;

import com.ai.resource.dto.ResourceAssetsResponse;
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

    @GetMapping("/assets")
    public ResourceAssetsResponse listAssets(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "resourceType", required = false) String resourceType,
            @RequestParam(value = "status", required = false) String status) {
        return resourceCenterService.listAssets(keyword, resourceType, status);
    }

    @GetMapping("/summary")
    public ResourceSummaryResponse getSummary() {
        return resourceCenterService.getSummary();
    }
}
