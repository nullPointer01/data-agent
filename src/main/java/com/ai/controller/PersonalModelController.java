package com.ai.controller;

import com.ai.api.ListPageResponse;
import com.ai.modelconfig.dto.PersonalModelOptionResponse;
import com.ai.service.ModelConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
    public ListPageResponse<PersonalModelOptionResponse> listModels(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String query) {
        return modelConfigService.listPersonalModelOptions(page, size, query);
    }
}
