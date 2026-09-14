package com.ai.controller;

import com.ai.skill.dto.SkillHistoryListResponse;
import com.ai.skill.dto.SkillListResponse;
import com.ai.skill.dto.SkillMutationResponse;
import com.ai.skill.dto.SkillRequest;
import com.ai.service.SkillGenerator;
import com.ai.service.SkillService;
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

import java.util.Map;

/**
 * [正式管理功能] Skill 的创建、更新、版本回滚和手动生成接口。
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/skills")
public class SkillUploadController {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkillUploadController.class);

    private final SkillService skillService;
    private final SkillGenerator skillGenerator;

    public SkillUploadController(SkillService skillService, SkillGenerator skillGenerator) {
        this.skillService = skillService;
        this.skillGenerator = skillGenerator;
    }

    /** 创建 Skill 配置并注册到运行时。 */
    @PostMapping("/create")
    public SkillMutationResponse createSkill(@RequestBody SkillRequest request) {
        LOGGER.info("Create skill request: {}", request.name());
        return skillService.createSkill(request);
    }

    /** 更新 Skill 配置，同时保留新的历史版本。 */
    @PutMapping("/update/{skillId}")
    public SkillMutationResponse updateSkill(@PathVariable String skillId, @RequestBody SkillRequest request) {
        return skillService.updateSkill(skillId, request);
    }

    /** 查询指定 Skill 的版本历史。 */
    @GetMapping("/{skillId}/history")
    public SkillHistoryListResponse getHistory(@PathVariable String skillId) {
        return skillService.listHistory(skillId);
    }

    /** 将 Skill 配置回滚到指定历史版本。 */
    @PostMapping("/{skillId}/rollback/{version}")
    public SkillMutationResponse rollback(@PathVariable String skillId, @PathVariable int version) {
        return skillService.rollbackSkill(skillId, version);
    }

    /**
     * 由管理员手工提交样例数据和目标，调用模型生成可编辑的 Skill 草稿。
     *
     * <p>该接口不会在文件上传、普通对话或评分后自动触发。</p>
     */
    @PostMapping("/generate")
    public Map<String, Object> generateSkill(@RequestBody Map<String, String> request) {
        String data = request.get("data");
        String description = request.get("description");
        LOGGER.info("Generate skill from data, description: {}", description);
        return skillGenerator.generateFromData(data, description);
    }

    /** 列出当前租户的 Skill 配置。 */
    @GetMapping("/list")
    public SkillListResponse listSkills() {
        return skillService.listSkills();
    }

    /** 删除 Skill 配置、历史版本及运行时注册。 */
    @DeleteMapping("/delete/{skillId}")
    public SkillMutationResponse deleteSkill(@PathVariable String skillId) {
        return skillService.deleteSkill(skillId);
    }

    /** 切换 Skill 启用状态，并同步运行时注册表。 */
    @PutMapping("/toggle/{skillId}")
    public SkillMutationResponse toggleSkill(@PathVariable String skillId) {
        return skillService.toggleSkill(skillId);
    }
}
