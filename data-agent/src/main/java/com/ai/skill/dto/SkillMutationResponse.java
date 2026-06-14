package com.ai.skill.dto;

/**
 * 技能变更命令的响应。
 *
 * @author data-agent
 */
public record SkillMutationResponse(
        boolean success,
        String message,
        String skillId,
        String name,
        Boolean enabled) {

    public static SkillMutationResponse created(String skillId, String name) {
        return new SkillMutationResponse(true, "Skill创建成功", skillId, name, null);
    }

    public static SkillMutationResponse updated(int historyVersion) {
        return new SkillMutationResponse(true, "Skill更新成功，旧版本已保存为 v" + historyVersion, null, null, null);
    }

    public static SkillMutationResponse deleted() {
        return new SkillMutationResponse(true, "Skill删除成功", null, null, null);
    }

    public static SkillMutationResponse toggled(String skillId, boolean enabled) {
        return new SkillMutationResponse(true, "Skill状态更新成功", skillId, null, enabled);
    }

    public static SkillMutationResponse rolledBack(int version) {
        return new SkillMutationResponse(true, "已回滚到 v" + version, null, null, null);
    }

    public static SkillMutationResponse failure(String message) {
        return new SkillMutationResponse(false, message, null, null, null);
    }
}
