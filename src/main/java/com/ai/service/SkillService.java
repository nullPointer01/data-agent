package com.ai.service;

import com.ai.skill.dto.SkillHistoryListResponse;
import com.ai.skill.dto.SkillListResponse;
import com.ai.skill.dto.SkillMutationResponse;
import com.ai.skill.dto.SkillRequest;

import java.util.Map;

/**
 * 技能管理应用服务接口。
 *
 * @author data-agent
 */
public interface SkillService {

    /**
     * 根据请求配置创建技能。
     *
     * @param skillConfig 技能配置
     * @return 操作结果
     */
    SkillMutationResponse createSkill(SkillRequest skillConfig);

    /**
     * 查询当前技能列表。
     *
     * @return 技能列表
     */
    SkillListResponse listSkills();

    /**
     * 删除技能。
     *
     * @param skillId 技能编号
     * @return 操作结果
     */
    SkillMutationResponse deleteSkill(String skillId);

    /**
     * 启用或禁用技能。
     *
     * @param skillId 技能编号
     * @return 操作结果
     */
    SkillMutationResponse toggleSkill(String skillId);

    /**
     * 更新技能并保存提示词历史。
     *
     * @param skillId 技能编号
     * @param request 更新请求
     * @return 操作结果
     */
    SkillMutationResponse updateSkill(String skillId, SkillRequest request);

    /**
     * 查询技能提示词历史。
     *
     * @param skillId 技能编号
     * @return 历史记录列表
     */
    SkillHistoryListResponse listHistory(String skillId);

    /**
     * 将技能提示词回滚到历史版本。
     *
     * @param skillId 技能编号
     * @param version 目标版本号
     * @return 操作结果
     */
    SkillMutationResponse rollbackSkill(String skillId, int version);

    /**
     * 执行技能。
     *
     * @param skillId 技能编号
     * @param query 用户查询
     * @param data 可选数据
     * @return 执行结果
     */
    Map<String, Object> executeSkill(String skillId, String query, Map<String, Object> data);
}
