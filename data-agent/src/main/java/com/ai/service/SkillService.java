package com.ai.service;

import java.util.Map;

public interface SkillService {
    Map<String, Object> createSkill(Map<String, Object> skillConfig);
    Map<String, Object> listSkills();
    Map<String, Object> deleteSkill(String skillId);
    Map<String, Object> toggleSkill(String skillId);
    Map<String, Object> executeSkill(String skillId, String query, Map<String, Object> data);
}
