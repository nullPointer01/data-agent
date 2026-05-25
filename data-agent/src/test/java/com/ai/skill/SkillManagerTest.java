package com.ai.skill;

import com.ai.mcp.McpContextManager;
import com.ai.mcp.McpModelService;
import com.ai.service.VectorMemoryService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillManagerTest {

    @Test
    void registerSkillShouldReplaceSameNameSkill() {
        SkillManager manager = newManager();
        Skill first = skill("销售分析", "旧描述");
        Skill second = skill("销售分析", "新描述");

        manager.registerSkill(first);
        manager.registerSkill(second);

        assertEquals(1, manager.getAllSkills().size());
        assertSame(second, manager.findSkillByName("销售分析"));
    }

    @Test
    void registerSkillWithoutVectorRefreshShouldOnlyUpdateRegistry() {
        VectorMemoryService vectorMemoryService = mock(VectorMemoryService.class);
        SkillManager manager = new SkillManager(mock(McpContextManager.class), mock(McpModelService.class),
                vectorMemoryService, new SkillMatcher(vectorMemoryService));
        Skill skill = skill("启动加载技能", "启动时只注册内存");

        manager.registerSkillWithoutVectorRefresh(skill);

        assertSame(skill, manager.findSkillByName("启动加载技能"));
        verify(vectorMemoryService, never()).removeFromStore("skill", "启动加载技能");
        verify(vectorMemoryService, never()).indexSkill("启动加载技能", "启动时只注册内存", "");
    }

    @Test
    void registerDefaultSkillWithoutVectorRefreshShouldSetDefaultOnly() {
        VectorMemoryService vectorMemoryService = mock(VectorMemoryService.class);
        SkillManager manager = new SkillManager(mock(McpContextManager.class), mock(McpModelService.class),
                vectorMemoryService, new SkillMatcher(vectorMemoryService));
        Skill skill = skill("默认启动技能", "启动时只注册默认技能");

        manager.registerDefaultSkillWithoutVectorRefresh(skill);

        assertSame(skill, manager.getDefaultSkill());
        verify(vectorMemoryService, never()).removeFromStore("skill", "默认启动技能");
        verify(vectorMemoryService, never()).indexSkill("默认启动技能", "启动时只注册默认技能", "");
    }

    @Test
    void processWithCommandShouldHandleSlashCommand() {
        McpContextManager contextManager = mock(McpContextManager.class);
        McpModelService modelService = mock(McpModelService.class);
        SkillManager manager = new SkillManager(contextManager, modelService, mock(VectorMemoryService.class),
                mock(SkillMatcher.class));
        Skill skill = mock(Skill.class);
        when(skill.getName()).thenReturn("help");
        when(skill.getDescription()).thenReturn("帮助");
        when(contextManager.createContext("help", "default")).thenReturn("ctx-1");
        when(skill.processWithContext("status", null, "ctx-1", modelService))
                .thenReturn("ok");

        manager.registerSkill(skill);

        String result = manager.processWithCommand("/help status", null);

        assertEquals("ok", result);
        verify(contextManager).destroyContext("ctx-1");
    }

    @Test
    void processWithCommandShouldReturnNullForBlankCommand() {
        SkillManager manager = newManager();

        assertNull(manager.processWithCommand("   ", null));
        assertNull(manager.processWithCommand(null, null));
    }

    private SkillManager newManager() {
        VectorMemoryService vms = mock(VectorMemoryService.class);
        return new SkillManager(mock(McpContextManager.class), mock(McpModelService.class),
                vms, new SkillMatcher(vms));
    }

    private Skill skill(String name, String description) {
        return new Skill() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getDescription() {
                return description;
            }

            @Override
            public boolean canHandle(String query) {
                return false;
            }

            @Override
            public String process(String query, Object data) {
                return null;
            }

            @Override
            public String processWithContext(String query, Object data, String contextId, McpModelService modelService) {
                return null;
            }

            @Override
            public String processWithContext(String query, Object data, String contextId, McpModelService modelService,
                    String modelId) {
                return null;
            }
        };
    }
}
