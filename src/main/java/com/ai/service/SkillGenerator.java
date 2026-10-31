package com.ai.service;

import com.ai.skill.SkillLearningService;
import org.springframework.stereotype.Service;
import java.util.Map;

/** 管理页生成可编辑草稿，用户确认创建前不写入、不启用、不绑定正式 Skill。 */
@Service
public class SkillGenerator {
    private final SkillLearningService learning;

    public SkillGenerator(SkillLearningService learning) { this.learning = learning; }

    /** 复用严格结构化提炼合同，移除旧的手工 JSON 截取和生成即启用路径。 */
    public Map<String, Object> generateFromData(String data, String description) {
        var draft = learning.manual(data, description);
        if (!draft.reusable()) return Map.of("success", false, "message", "未找到可复用的技能方法");
        return Map.of("success", true, "message", "草稿已生成，请检查并确认创建",
                "name", draft.name(), "description", draft.description(), "promptTemplate", draft.instructions(),
                "steps", draft.steps(), "toolDependencies", String.join("\n", draft.dependencies()));
    }
}
