package com.ai.controller;

import com.ai.modelconfig.dto.PersonalModelOptionsResponse;
import com.ai.service.ModelConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个人 Agent 模型选择接口。
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/my/models")
public class PersonalModelController {

    private final ModelConfigService modelConfigService;

    public PersonalModelController(ModelConfigService modelConfigService) {
        this.modelConfigService = modelConfigService;
    }

    /**
     * 查询当前用户可选择的已启用模型。
     *
     * @return 脱敏模型目录
     */
    @GetMapping
    public PersonalModelOptionsResponse listModels() {
        return modelConfigService.listPersonalModelOptions();
    }
}
