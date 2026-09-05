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
 * REST API for skill management.
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

    @PostMapping("/create")
    public SkillMutationResponse createSkill(@RequestBody SkillRequest request) {
        LOGGER.info("Create skill request: {}", request.name());
        return skillService.createSkill(request);
    }

    @PutMapping("/update/{skillId}")
    public SkillMutationResponse updateSkill(@PathVariable String skillId, @RequestBody SkillRequest request) {
        return skillService.updateSkill(skillId, request);
    }

    @GetMapping("/{skillId}/history")
    public SkillHistoryListResponse getHistory(@PathVariable String skillId) {
        return skillService.listHistory(skillId);
    }

    @PostMapping("/{skillId}/rollback/{version}")
    public SkillMutationResponse rollback(@PathVariable String skillId, @PathVariable int version) {
        return skillService.rollbackSkill(skillId, version);
    }

    @PostMapping("/generate")
    public Map<String, Object> generateSkill(@RequestBody Map<String, String> request) {
        String data = request.get("data");
        String description = request.get("description");
        LOGGER.info("Generate skill from data, description: {}", description);
        return skillGenerator.generateFromData(data, description);
    }

    @PostMapping("/generate-from-conversation")
    public Map<String, Object> generateFromConversation(@RequestBody Map<String, String> request) {
        String sessionId = request.get("sessionId");
        LOGGER.info("Generate skill from conversation, session: {}", sessionId);
        return skillGenerator.generateFromConversation(sessionId);
    }

    @PostMapping("/feedback/{skillId}")
    public Map<String, Object> recordFeedback(@PathVariable String skillId, @RequestBody Map<String, Object> body) {
        boolean positive = Boolean.TRUE.equals(body.get("positive"));
        return skillGenerator.recordFeedback(skillId, positive);
    }

    @GetMapping("/list")
    public SkillListResponse listSkills() {
        return skillService.listSkills();
    }

    @DeleteMapping("/delete/{skillId}")
    public SkillMutationResponse deleteSkill(@PathVariable String skillId) {
        return skillService.deleteSkill(skillId);
    }

    @PutMapping("/toggle/{skillId}")
    public SkillMutationResponse toggleSkill(@PathVariable String skillId) {
        return skillService.toggleSkill(skillId);
    }
}
