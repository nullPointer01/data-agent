package com.ai.service;

import com.ai.model.SkillConfig;
import com.ai.model.SkillPromptHistory;
import com.ai.repository.SkillConfigRepository;
import com.ai.repository.SkillPromptHistoryRepository;
import com.ai.security.SecurityContextHelper;
import com.ai.skill.SkillManager;
import com.ai.skill.dto.SkillHistoryListResponse;
import com.ai.skill.dto.SkillMutationResponse;
import com.ai.skill.dto.SkillRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SkillServiceImplTest {

    @Test
    void updateSavesHistoryWithinCurrentTenant() {
        SkillConfigRepository skillRepository = mock(SkillConfigRepository.class);
        SkillPromptHistoryRepository historyRepository = mock(SkillPromptHistoryRepository.class);
        SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
        SkillConfig skill = buildSkill();

        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-1");
        when(skillRepository.findBySkillIdAndTenantId("skill-1", "tenant-1")).thenReturn(Optional.of(skill));
        when(historyRepository.findTopBySkillIdAndTenantIdOrderByVersionDesc("skill-1", "tenant-1"))
                .thenReturn(Optional.empty());

        SkillServiceImpl service = newService(skillRepository, historyRepository, securityContextHelper);
        SkillRequest request = new SkillRequest(
                "销售分析", "new", null, null, null, null, "new prompt", null, null, "new steps", null, null,
                null, "手动调整");

        SkillMutationResponse response = service.updateSkill("skill-1", request);

        assertTrue(response.success());
        assertEquals("new prompt", skill.getPromptTemplate());
        verify(historyRepository).save(any(SkillPromptHistory.class));
        verify(skillRepository).save(skill);
    }

    @Test
    void historyIsTenantScoped() {
        SkillConfigRepository skillRepository = mock(SkillConfigRepository.class);
        SkillPromptHistoryRepository historyRepository = mock(SkillPromptHistoryRepository.class);
        SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);
        SkillConfig skill = buildSkill();
        SkillPromptHistory history = new SkillPromptHistory();
        history.setSkillId("skill-1");
        history.setTenantId("tenant-1");
        history.setVersion(1);

        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-1");
        when(skillRepository.findBySkillIdAndTenantId("skill-1", "tenant-1")).thenReturn(Optional.of(skill));
        when(historyRepository.findBySkillIdAndTenantIdOrderByVersionDesc("skill-1", "tenant-1"))
                .thenReturn(List.of(history));

        SkillServiceImpl service = newService(skillRepository, historyRepository, securityContextHelper);

        SkillHistoryListResponse response = service.listHistory("skill-1");

        assertTrue(response.success());
        assertEquals(1, response.history().size());
        verify(historyRepository).findBySkillIdAndTenantIdOrderByVersionDesc("skill-1", "tenant-1");
    }

    @Test
    void businessTenantCannotUpdateDefaultTenantSkill() {
        SkillConfigRepository skillRepository = mock(SkillConfigRepository.class);
        SkillPromptHistoryRepository historyRepository = mock(SkillPromptHistoryRepository.class);
        SecurityContextHelper securityContextHelper = mock(SecurityContextHelper.class);

        when(securityContextHelper.getCurrentTenantId()).thenReturn("tenant-1");
        when(skillRepository.findBySkillIdAndTenantId("skill-1", "tenant-1")).thenReturn(Optional.empty());

        SkillServiceImpl service = newService(skillRepository, historyRepository, securityContextHelper);
        SkillRequest request = new SkillRequest(
                "销售分析", "new", null, null, null, null, "new prompt", null, null, null, null, null, null, null);

        SkillMutationResponse response = service.updateSkill("skill-1", request);

        assertFalse(response.success());
    }

    private SkillServiceImpl newService(SkillConfigRepository skillRepository,
            SkillPromptHistoryRepository historyRepository,
            SecurityContextHelper securityContextHelper) {
        return new SkillServiceImpl(skillRepository, historyRepository, mock(SkillManager.class), securityContextHelper);
    }

    private SkillConfig buildSkill() {
        SkillConfig skill = new SkillConfig();
        skill.setSkillId("skill-1");
        skill.setName("销售分析");
        skill.setDescription("old");
        skill.setPromptTemplate("old prompt");
        skill.setSteps("old steps");
        skill.setTenantId("tenant-1");
        skill.setEnabled(true);
        return skill;
    }
}
