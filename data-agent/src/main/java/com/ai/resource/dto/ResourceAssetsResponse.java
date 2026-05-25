package com.ai.resource.dto;

import java.util.List;

/**
 * Resource asset list response.
 *
 * @author data-agent
 */
public class ResourceAssetsResponse {

    private ResourceSummaryResponse summary;

    private List<ResourceAssetResponse> assets;

    public ResourceSummaryResponse getSummary() {
        return summary;
    }

    public void setSummary(ResourceSummaryResponse summary) {
        this.summary = summary;
    }

    public List<ResourceAssetResponse> getAssets() {
        return assets;
    }

    public void setAssets(List<ResourceAssetResponse> assets) {
        this.assets = assets;
    }
}
