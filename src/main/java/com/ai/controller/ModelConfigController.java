package com.ai.controller;

import com.ai.modelconfig.dto.AvailableModelsRequest;
import com.ai.modelconfig.dto.AvailableModelsResponse;
import com.ai.modelconfig.dto.ModelConfigDetailResponse;
import com.ai.modelconfig.dto.ModelConfigListResponse;
import com.ai.modelconfig.dto.ModelConfigMutationResponse;
import com.ai.modelconfig.dto.ModelConfigRequest;
import com.ai.modelconfig.dto.ModelProbeRequest;
import com.ai.modelconfig.dto.ModelProbeResponse;
import com.ai.modelconfig.dto.ProviderCatalogResponse;
import com.ai.service.ModelConnectionProbeService;
import com.ai.service.ModelConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 租户维度模型配置的 REST 接口。
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/models")
public class ModelConfigController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelConfigController.class);

    private final ModelConfigService modelConfigService;
    private final ModelConnectionProbeService modelConnectionProbeService;

    public ModelConfigController(ModelConfigService modelConfigService,
            ModelConnectionProbeService modelConnectionProbeService) {
        this.modelConfigService = modelConfigService;
        this.modelConnectionProbeService = modelConnectionProbeService;
    }

    @PostMapping("/add")
    public ModelConfigMutationResponse addModel(@RequestBody ModelConfigRequest request) {
        LOGGER.info("Add model request: {}", request.name());
        return modelConfigService.addModel(request);
    }

    @PutMapping("/update/{modelId}")
    public ModelConfigMutationResponse updateModel(@PathVariable String modelId,
            @RequestBody ModelConfigRequest request) {
        return modelConfigService.updateModel(modelId, request);
    }

    @DeleteMapping("/delete/{modelId}")
    public ModelConfigMutationResponse deleteModel(@PathVariable String modelId) {
        return modelConfigService.deleteModel(modelId);
    }

    @PutMapping("/toggle/{modelId}")
    public ModelConfigMutationResponse toggleModel(@PathVariable String modelId) {
        return modelConfigService.toggleModel(modelId);
    }

    @GetMapping("/list")
    public ModelConfigListResponse listModels() {
        return modelConfigService.listModels();
    }

    @GetMapping("/get/{modelId}")
    public ModelConfigDetailResponse getModel(@PathVariable String modelId) {
        return modelConfigService.getModelDetail(modelId);
    }

    /**
     * 返回厂商目录：前端配置页的厂商下拉与默认值唯一来源。
     *
     * @return 厂商目录响应
     */
    @GetMapping("/providers")
    public ProviderCatalogResponse providers() {
        return ProviderCatalogResponse.fromCatalog();
    }

    /**
     * 拉取厂商可用模型列表，配置页用于一键选择模型名。
     *
     * @param request 拉取请求
     * @return 模型列表响应
     */
    @PostMapping("/available")
    public AvailableModelsResponse availableModels(@RequestBody AvailableModelsRequest request) {
        LOGGER.info("Fetch available models request: provider={}", request.provider());
        return modelConfigService.fetchAvailableModels(request);
    }

    /** 使用临时客户端执行一次真实 Chat 请求，不保存配置或回复。 */
    @PostMapping("/probe")
    public ModelProbeResponse probe(@RequestBody ModelProbeRequest request) {
        LOGGER.info("Probe model connection request: provider={}", request.provider());
        return modelConnectionProbeService.probe(request);
    }
}
