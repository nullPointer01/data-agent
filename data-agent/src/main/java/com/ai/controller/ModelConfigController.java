package com.ai.controller;

import com.ai.model.ModelConfig;
import com.ai.service.ModelConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/models")
public class ModelConfigController {

    private static final Logger log = LoggerFactory.getLogger(ModelConfigController.class);
    private final ModelConfigService modelConfigService;

    public ModelConfigController(ModelConfigService modelConfigService) {
        this.modelConfigService = modelConfigService;
    }

    @PostMapping("/add")
    public Map<String, Object> addModel(@RequestBody ModelConfig modelConfig) {
        log.info("Add model request: {}", modelConfig.getName());
        return modelConfigService.addModel(modelConfig);
    }

    @PutMapping("/update/{modelId}")
    public Map<String, Object> updateModel(@PathVariable String modelId, @RequestBody ModelConfig modelConfig) {
        return modelConfigService.updateModel(modelId, modelConfig);
    }

    @DeleteMapping("/delete/{modelId}")
    public Map<String, Object> deleteModel(@PathVariable String modelId) {
        return modelConfigService.deleteModel(modelId);
    }

    @PutMapping("/toggle/{modelId}")
    public Map<String, Object> toggleModel(@PathVariable String modelId) {
        return modelConfigService.toggleModel(modelId);
    }

    @GetMapping("/list")
    public Map<String, Object> listModels() {
        return modelConfigService.listModels();
    }

    @GetMapping("/get/{modelId}")
    public Map<String, Object> getModel(@PathVariable String modelId) {
        ModelConfig config = modelConfigService.getModel(modelId);
        if (config != null) {
            return Map.of("success", true, "model", config);
        }
        return Map.of("success", false, "message", "模型不存在");
    }
}
