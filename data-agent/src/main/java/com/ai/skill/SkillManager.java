package com.ai.skill;

import com.ai.mcp.MCPContextManager;
import com.ai.mcp.MCPModelService;
import com.ai.service.VectorMemoryService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SkillManager {

    private static final Logger log = LoggerFactory.getLogger(SkillManager.class);
    private static final double SEMANTIC_MATCH_THRESHOLD = 0.6;

    private final List<Skill> skills = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, Skill> skillByName = new ConcurrentHashMap<>();
    private volatile Skill defaultSkill;
    private final MCPContextManager mcpContextManager;
    private final MCPModelService mcpModelService;
    private final VectorMemoryService vectorMemoryService;

    public SkillManager(MCPContextManager mcpContextManager, MCPModelService mcpModelService,
            VectorMemoryService vectorMemoryService) {
        this.mcpContextManager = mcpContextManager;
        this.mcpModelService = mcpModelService;
        this.vectorMemoryService = vectorMemoryService;
    }

    public void registerSkill(Skill skill) {
        skills.add(skill);
        skillByName.put(skill.getName().toLowerCase(), skill);
        try {
            vectorMemoryService.indexSkill(skill.getName(), skill.getDescription(), "");
        } catch (Exception e) {
            log.warn("Failed to index skill to vector memory: {}", skill.getName(), e);
        }
        log.info("Skill registered: {}", skill.getName());
    }

    public void registerDefaultSkill(Skill skill) {
        this.defaultSkill = skill;
        skills.add(skill);
        skillByName.put(skill.getName().toLowerCase(), skill);
        try {
            vectorMemoryService.indexSkill(skill.getName(), skill.getDescription(), "");
        } catch (Exception e) {
            log.warn("Failed to index default skill to vector memory: {}", skill.getName(), e);
        }
        log.info("Default skill registered: {}", skill.getName());
    }

    public void unregisterSkill(String name) {
        skills.removeIf(s -> s.getName().equalsIgnoreCase(name));
        skillByName.remove(name.toLowerCase());
        if (defaultSkill != null && defaultSkill.getName().equalsIgnoreCase(name)) {
            defaultSkill = null;
        }
        try {
            vectorMemoryService.removeFromStore("skill", name);
        } catch (Exception e) {
            log.warn("Failed to remove skill from vector store: {}", name, e);
        }
    }

    public Skill getDefaultSkill() {
        return defaultSkill;
    }

    public List<Skill> getAllSkills() {
        return List.copyOf(skills);
    }

    public Skill findSkill(String query) {
        SkillMatchResult result = findSkillWithScore(query);
        return result != null ? result.skill : null;
    }

    public SkillMatchResult findSkillWithScore(String query) {
        SkillMatchResult bestMatch = null;
        for (Skill skill : skills) {
            if (skill.canHandle(query)) {
                double score = calculateMatchScore(query, skill);
                if (bestMatch == null || score > bestMatch.score) {
                    bestMatch = new SkillMatchResult(skill, score);
                }
            }
        }

        try {
            List<EmbeddingMatch<TextSegment>> semanticMatches =
                    vectorMemoryService.searchMatches(query, 3, SEMANTIC_MATCH_THRESHOLD);
            for (EmbeddingMatch<TextSegment> match : semanticMatches) {
                TextSegment segment = match.embedded();
                String type = segment.metadata().getString("type");
                if (!"skill".equals(type)) continue;
                String skillText = segment.text();
                for (Skill skill : skills) {
                    if (skillText.contains("技能[" + skill.getName() + "]")) {
                        double semanticScore = match.score() * 15;
                        if (bestMatch == null || semanticScore > bestMatch.score) {
                            bestMatch = new SkillMatchResult(skill, semanticScore);
                        }
                        break;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Semantic skill matching failed, using keyword only: {}", e.getMessage());
        }

        return bestMatch;
    }

    public Skill findSkillByName(String name) {
        if (name == null)
            return null;
        Skill skill = skillByName.get(name.toLowerCase());
        if (skill != null)
            return skill;
        for (Skill s : skills) {
            if (s.getName().equalsIgnoreCase(name))
                return s;
        }
        return null;
    }

    private double calculateMatchScore(String query, Skill skill) {
        double score = 0;
        String lowerQuery = query.toLowerCase();
        if (lowerQuery.contains(skill.getName().toLowerCase())) {
            score += 10;
        }
        if (skill.canHandle(query)) {
            score += 5;
        }
        if (skill.getDescription() != null) {
            String[] descWords = skill.getDescription().toLowerCase().split("\\s+");
            for (String word : descWords) {
                if (lowerQuery.contains(word) && word.length() > 1) {
                    score += 2;
                }
            }
        }
        return score;
    }

    public String processWithSkill(String query, Object data) {
        Skill skill = findSkill(query);
        if (skill == null)
            skill = defaultSkill;
        if (skill != null) {
            String contextId = mcpContextManager.createContext(skill.getName(), "default");
            try {
                return skill.processWithContext(query, data, contextId, mcpModelService);
            } finally {
                mcpContextManager.destroyContext(contextId);
            }
        }
        return null;
    }

    public String processWithSkillByName(String skillName, String query, Object data) {
        Skill skill = findSkillByName(skillName);
        if (skill == null)
            skill = defaultSkill;
        if (skill != null) {
            String contextId = mcpContextManager.createContext(skill.getName(), "default");
            try {
                return skill.processWithContext(query, data, contextId, mcpModelService);
            } finally {
                mcpContextManager.destroyContext(contextId);
            }
        }
        return null;
    }

    public String processWithCommand(String command, Object data) {
        if (command.startsWith("/")) {
            String[] parts = command.split(" ", 2);
            String skillName = parts[0].substring(1);
            String actualQuery = parts.length > 1 ? parts[1] : "";
            return processWithSkillByName(skillName, actualQuery, data);
        }
        return null;
    }

    public static class SkillMatchResult {
        public final Skill skill;
        public final double score;

        public SkillMatchResult(Skill skill, double score) {
            this.skill = skill;
            this.score = score;
        }
    }
}
