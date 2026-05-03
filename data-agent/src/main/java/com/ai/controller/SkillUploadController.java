package com.ai.controller;

import com.ai.service.SkillGenerator;
import com.ai.service.SkillService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/skills")
public class SkillUploadController {

    private static final Logger log = LoggerFactory.getLogger(SkillUploadController.class);
    private final SkillService skillService;
    private final SkillGenerator skillGenerator;

    public SkillUploadController(SkillService skillService, SkillGenerator skillGenerator) {
        this.skillService = skillService;
        this.skillGenerator = skillGenerator;
    }

    @PostMapping("/create")
    public Map<String, Object> createSkill(@RequestBody Map<String, Object> skillConfig) {
        log.info("Create skill request: {}", skillConfig.get("name"));
        return skillService.createSkill(skillConfig);
    }

    @PostMapping("/generate")
    public Map<String, Object> generateSkill(@RequestBody Map<String, String> request) {
        String data = request.get("data");
        String description = request.get("description");
        log.info("Generate skill from data, description: {}", description);
        return skillGenerator.generateFromData(data, description);
    }

    @PostMapping("/generate-from-conversation")
    public Map<String, Object> generateFromConversation(@RequestBody Map<String, String> request) {
        String sessionId = request.get("sessionId");
        log.info("Generate skill from conversation, session: {}", sessionId);
        return skillGenerator.generateFromConversation(sessionId);
    }

    @PostMapping("/feedback/{skillId}")
    public Map<String, Object> recordFeedback(@PathVariable String skillId, @RequestBody Map<String, Object> body) {
        boolean positive = Boolean.TRUE.equals(body.get("positive"));
        return skillGenerator.recordFeedback(skillId, positive);
    }

    @GetMapping("/list")
    public Map<String, Object> listSkills() {
        return skillService.listSkills();
    }

    @DeleteMapping("/delete/{skillId}")
    public Map<String, Object> deleteSkill(@PathVariable String skillId) {
        return skillService.deleteSkill(skillId);
    }

    @PutMapping("/toggle/{skillId}")
    public Map<String, Object> toggleSkill(@PathVariable String skillId) {
        return skillService.toggleSkill(skillId);
    }
}
