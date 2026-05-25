package com.ai.service;

import com.ai.skill.dto.SkillHistoryListResponse;
import com.ai.skill.dto.SkillListResponse;
import com.ai.skill.dto.SkillMutationResponse;
import com.ai.skill.dto.SkillRequest;

import java.util.Map;

/**
 * Skill management application service.
 *
 * @author data-agent
 */
public interface SkillService {

    /**
     * Create a skill from request config.
     *
     * @param skillConfig skill config
     * @return operation result
     */
    SkillMutationResponse createSkill(SkillRequest skillConfig);

    /**
     * List current skills.
     *
     * @return skills result
     */
    SkillListResponse listSkills();

    /**
     * Delete a skill.
     *
     * @param skillId skill id
     * @return operation result
     */
    SkillMutationResponse deleteSkill(String skillId);

    /**
     * Enable or disable a skill.
     *
     * @param skillId skill id
     * @return operation result
     */
    SkillMutationResponse toggleSkill(String skillId);

    /**
     * Update a skill and save prompt history.
     *
     * @param skillId skill id
     * @param request update request
     * @return operation result
     */
    SkillMutationResponse updateSkill(String skillId, SkillRequest request);

    /**
     * List skill prompt history.
     *
     * @param skillId skill id
     * @return history result
     */
    SkillHistoryListResponse listHistory(String skillId);

    /**
     * Roll back a skill prompt to a historical version.
     *
     * @param skillId skill id
     * @param version target version
     * @return operation result
     */
    SkillMutationResponse rollbackSkill(String skillId, int version);

    /**
     * Execute a skill.
     *
     * @param skillId skill id
     * @param query user query
     * @param data optional data
     * @return execution result
     */
    Map<String, Object> executeSkill(String skillId, String query, Map<String, Object> data);
}
