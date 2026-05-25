package com.ai.agent.tool;

import com.ai.skill.Skill;
import com.ai.skill.SkillManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentSkillToolServiceTest {

    @Test
    void listAvailableSkillsFormatsRegisteredSkills() {
        SkillManager skillManager = mock(SkillManager.class);
        Skill skill = mock(Skill.class);
        when(skill.getName()).thenReturn("销售分析");
        when(skill.getDescription()).thenReturn("分析订单趋势");
        when(skillManager.getAllSkills()).thenReturn(List.of(skill));

        AgentSkillToolService service = new AgentSkillToolService(skillManager);

        String result = service.listAvailableSkills();

        assertEquals("- 销售分析: 分析订单趋势", result);
    }

    @Test
    void useSkillReturnsAvailableSkillsWhenSkillMissing() {
        SkillManager skillManager = mock(SkillManager.class);
        when(skillManager.getAllSkills()).thenReturn(List.of());
        when(skillManager.processWithSkillByName("unknown", "hello", null)).thenReturn(null);

        AgentSkillToolService service = new AgentSkillToolService(skillManager);

        String result = service.useSkill("unknown", "hello");

        assertTrue(result.contains("技能 'unknown' 不存在或执行失败"));
        assertTrue(result.contains("当前没有可用的技能"));
    }
}
