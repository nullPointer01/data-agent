package com.ai.skill;

/** 主动学习只接受用户长期方法或本次真实成功工具流程，不接受模型声称的经历。 */
public enum SkillLearningSource {
    USER_PROCEDURE,
    EXECUTED_WORKFLOW
}
