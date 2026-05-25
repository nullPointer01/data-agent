package com.ai.skill;

import com.ai.service.VectorMemoryService;
import com.ai.vector.VectorDocumentTypes;
import com.ai.vector.VectorMetadataKeys;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * 技能匹配器，负责从技能列表中按关键词和语义相似度匹配最合适的技能。
 *
 * @author data-agent
 */
@Component
class SkillMatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkillMatcher.class);
    private static final double SEMANTIC_MATCH_THRESHOLD = 0.6D;
    private static final int SEMANTIC_MATCH_TOP_K = 3;
    private static final int SEMANTIC_SCORE_MULTIPLIER = 15;
    private static final int NAME_MATCH_SCORE = 10;
    private static final int CAN_HANDLE_SCORE = 5;
    private static final int DESCRIPTION_WORD_SCORE = 2;
    private static final int MIN_DESCRIPTION_WORD_LENGTH = 1;
    private static final String SKILL_TEXT_PREFIX = "技能[";
    private static final String SKILL_TEXT_SUFFIX = "]";

    private final VectorMemoryService vectorMemoryService;

    SkillMatcher(VectorMemoryService vectorMemoryService) {
        this.vectorMemoryService = vectorMemoryService;
    }

    SkillManager.SkillMatchResult findBestMatch(String query, List<Skill> skillSnapshot) {
        SkillManager.SkillMatchResult bestMatch = findBestKeywordMatch(query, skillSnapshot);
        try {
            SkillManager.SkillMatchResult semanticMatch = findBestSemanticMatch(query, skillSnapshot);
            bestMatch = betterMatch(bestMatch, semanticMatch);
        } catch (Exception e) {
            LOGGER.debug("技能语义匹配失败，改用关键词匹配: {}", e.getMessage());
        }
        return bestMatch;
    }

    double calculateMatchScore(String query, Skill skill) {
        double score = 0;
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        if (lowerQuery.contains(skill.getName().toLowerCase(Locale.ROOT))) {
            score += NAME_MATCH_SCORE;
        }
        if (skill.canHandle(query)) {
            score += CAN_HANDLE_SCORE;
        }
        if (skill.getDescription() != null) {
            String[] descWords = skill.getDescription().toLowerCase(Locale.ROOT).split("\\s+");
            for (String word : descWords) {
                if (lowerQuery.contains(word) && word.length() > MIN_DESCRIPTION_WORD_LENGTH) {
                    score += DESCRIPTION_WORD_SCORE;
                }
            }
        }
        return score;
    }

    private SkillManager.SkillMatchResult findBestKeywordMatch(String query, List<Skill> skillSnapshot) {
        SkillManager.SkillMatchResult bestMatch = null;
        for (Skill skill : skillSnapshot) {
            if (skill.canHandle(query)) {
                SkillManager.SkillMatchResult currentMatch =
                        new SkillManager.SkillMatchResult(skill, calculateMatchScore(query, skill));
                bestMatch = betterMatch(bestMatch, currentMatch);
            }
        }
        return bestMatch;
    }

    private SkillManager.SkillMatchResult findBestSemanticMatch(String query, List<Skill> skillSnapshot) {
        SkillManager.SkillMatchResult bestMatch = null;
        List<EmbeddingMatch<TextSegment>> semanticMatches =
                vectorMemoryService.searchMatches(query, SEMANTIC_MATCH_TOP_K, SEMANTIC_MATCH_THRESHOLD);
        for (EmbeddingMatch<TextSegment> match : semanticMatches) {
            Skill matchedSkill = findSemanticMatchedSkill(match.embedded(), skillSnapshot);
            if (matchedSkill != null) {
                SkillManager.SkillMatchResult currentMatch =
                        new SkillManager.SkillMatchResult(matchedSkill, match.score() * SEMANTIC_SCORE_MULTIPLIER);
                bestMatch = betterMatch(bestMatch, currentMatch);
            }
        }
        return bestMatch;
    }

    private Skill findSemanticMatchedSkill(TextSegment segment, List<Skill> skillSnapshot) {
        String type = segment.metadata().getString(VectorMetadataKeys.TYPE);
        if (!VectorDocumentTypes.SKILL.equals(type)) {
            return null;
        }
        String skillText = segment.text();
        for (Skill skill : skillSnapshot) {
            if (skillText.contains(SKILL_TEXT_PREFIX + skill.getName() + SKILL_TEXT_SUFFIX)) {
                return skill;
            }
        }
        return null;
    }

    private SkillManager.SkillMatchResult betterMatch(SkillManager.SkillMatchResult bestMatch,
            SkillManager.SkillMatchResult currentMatch) {
        if (currentMatch == null) {
            return bestMatch;
        }
        if (bestMatch == null || currentMatch.score > bestMatch.score) {
            return currentMatch;
        }
        return bestMatch;
    }
}
