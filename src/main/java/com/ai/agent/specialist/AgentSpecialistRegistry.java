package com.ai.agent.specialist;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import com.ai.agent.AgentType;

/**
 * Agent 专家注册表，管理所有已注册的 {@link AgentSpecialist} 实例。
 *
 * <p>通过 Spring 自动注入所有专家实现，按 {@link AgentType} 索引。
 * 当请求的类型未注册时，自动回退到 {@link AgentType#REACT} 专家。</p>
 *
 * @author data-agent
 */
@Component
public class AgentSpecialistRegistry {

    private final Map<AgentType, AgentSpecialist> specialists = new EnumMap<>(AgentType.class);

    /**
     * 构造注册表，注入所有可用的专家实现。
     *
     * @param specialistList Spring 容器中所有 AgentSpecialist Bean
     */
    public AgentSpecialistRegistry(List<AgentSpecialist> specialistList) {
        for (AgentSpecialist specialist : specialistList) {
            specialists.put(specialist.type(), specialist);
        }
    }

    /**
     * 根据类型解析专家实现。找不到时回退到 REACT 专家。
     *
     * @param type Agent 类型
     * @return 对应的专家实现
     */
    public AgentSpecialist resolve(AgentType type) {
        AgentSpecialist specialist = specialists.get(type);
        if (specialist != null) {
            return specialist;
        }
        return specialists.get(AgentType.REACT);
    }

    /**
     * 按类型查找专家，不做回退。
     *
     * @param type Agent 类型
     * @return 专家实现的 Optional
     */
    public Optional<AgentSpecialist> findByType(AgentType type) {
        return Optional.ofNullable(specialists.get(type));
    }

    /**
     * 判断指定类型是否有运行时可用的专家。
     *
     * @param type Agent 类型
     * @return 是否已注册
     */
    public boolean isRuntimeAvailable(AgentType type) {
        return specialists.containsKey(type);
    }

    /**
     * 返回所有已注册的 Agent 类型集合。
     *
     * @return 已注册类型集合
     */
    public Set<AgentType> registeredTypes() {
        return Set.copyOf(specialists.keySet());
    }

    /**
     * 返回已注册专家数量。
     *
     * @return 专家数量
     */
    public int size() {
        return specialists.size();
    }
}
