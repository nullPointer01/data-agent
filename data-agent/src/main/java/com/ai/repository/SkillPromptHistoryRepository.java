package com.ai.repository;

import com.ai.model.SkillPromptHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 技能提示历史仓储。
 *
 * @author data-agent
 */
public interface SkillPromptHistoryRepository extends JpaRepository<SkillPromptHistory, Long> {

    /**
     * 查询一个租户技能的历史记录。
     *
     * @param skillId 技能 ID
     * @param tenantId 租户 ID
     * @return 历史记录列表
     */
    List<SkillPromptHistory> findBySkillIdAndTenantIdOrderByVersionDesc(String skillId, String tenantId);

    /**
     * 查询一个租户技能的最新历史记录。
     *
     * @param skillId 技能 ID
     * @param tenantId 租户 ID
     * @return 最新历史记录
     */
    Optional<SkillPromptHistory> findTopBySkillIdAndTenantIdOrderByVersionDesc(String skillId, String tenantId);
}
