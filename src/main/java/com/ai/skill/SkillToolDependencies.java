package com.ai.skill;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/** 技能依赖只保存工具稳定名称，不保存地址、凭据或可执行代码。 */
public final class SkillToolDependencies {
    /** 服务端元数据标识：该规格属于潜在技能依赖，激活前不向模型开放。 */
    public static final String SCOPED_ONLY = "skill_scoped_only";
    private SkillToolDependencies() { }

    /** 管理页支持每行一个名称，也支持逗号分隔；限制单技能依赖规模。 */
    public static Set<String> parse(String value) {
        if (value == null || value.isBlank()) return Set.of();
        if (value.length() > 2400) throw new IllegalArgumentException("技能工具依赖过长");
        Set<String> names = Arrays.stream(value.trim().split("[,\\s]+"))
                .collect(Collectors.toUnmodifiableSet());
        if (names.size() > 24 || names.stream().anyMatch(name -> !name.matches("[A-Za-z][A-Za-z0-9_]{0,127}")
                || Set.of("activate_skill", "read_skill_resource", "delegateToAgent", "proposeSkill").contains(name))) {
            throw new IllegalArgumentException("技能最多依赖 24 个业务工具，不能依赖技能管理、学习或委派适配器");
        }
        return names;
    }

    /** 规范化保存以便版本比较，空依赖保持空字符串。 */
    public static String normalize(String value) {
        return parse(value).stream().sorted().collect(Collectors.joining("\n"));
    }
}
