package com.ai.controller;

import com.ai.modelconfig.dto.ModelConfigDetailResponse;
import com.ai.modelconfig.dto.ModelConfigListResponse;
import com.ai.modelconfig.dto.ModelConfigMutationResponse;
import com.ai.modelconfig.dto.ModelConfigRequest;
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
 * REST API for tenant-scoped model configuration.
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/models")
public class ModelConfigController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ModelConfigController.class);

    private final ModelConfigService modelConfigService;

    public ModelConfigController(ModelConfigService modelConfigService) {
        this.modelConfigService = modelConfigService;
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
}
