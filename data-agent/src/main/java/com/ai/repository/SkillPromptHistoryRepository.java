package com.ai.repository;

import com.ai.model.SkillPromptHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for skill prompt history.
 *
 * @author data-agent
 */
public interface SkillPromptHistoryRepository extends JpaRepository<SkillPromptHistory, Long> {

    /**
     * Finds history entries for one tenant skill.
     *
     * @param skillId skill id
     * @param tenantId tenant id
     * @return history entries
     */
    List<SkillPromptHistory> findBySkillIdAndTenantIdOrderByVersionDesc(String skillId, String tenantId);

    /**
     * Finds latest history entry for one tenant skill.
     *
     * @param skillId skill id
     * @param tenantId tenant id
     * @return latest history entry
     */
    Optional<SkillPromptHistory> findTopBySkillIdAndTenantIdOrderByVersionDesc(String skillId, String tenantId);
}
