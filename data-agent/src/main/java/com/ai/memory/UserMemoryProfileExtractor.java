package com.ai.memory;

import com.ai.memory.dto.UserMemoryProfileSnapshotResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从记忆条目中提取当前用户画像。
 *
 * @author data-agent
 */
@Component
public class UserMemoryProfileExtractor {

    private static final int MAX_LIST_SIZE = 6;
    private static final Pattern NAME_PATTERN = Pattern.compile("(?:我叫|我是|名字叫)([\\u4e00-\\u9fa5A-Za-z0-9_]{2,16})");
    private static final Pattern COMPANY_PATTERN = Pattern.compile("(?:我的公司|我们公司|所在公司|公司)(?:是|叫)?([\\u4e00-\\u9fa5A-Za-z0-9_\\-]{2,30})");
    private static final Pattern ROLE_PATTERN = Pattern.compile("(?:我是|担任|职位是|角色是|负责)([\\u4e00-\\u9fa5A-Za-z0-9_]{2,20})");
    private static final Map<String, String> INDUSTRY_KEYWORDS = Map.of(
            "电商", "电商",
            "零售", "零售",
            "金融", "金融",
            "教育", "教育",
            "医疗", "医疗",
            "制造", "制造",
            "物流", "物流");

    /**
     * 根据记忆列表生成结构化画像。
     *
     * @param memories 记忆列表
     * @return 用户画像
     */
    public UserMemoryProfileSnapshotResponse extract(List<MemoryEntry> memories) {
        if (memories == null || memories.isEmpty()) {
            return UserMemoryProfileSnapshotResponse.empty();
        }
        ProfileAccumulator accumulator = new ProfileAccumulator();
        for (MemoryEntry memory : memories) {
            String content = resolveContent(memory);
            if (!StringUtils.hasText(content)) {
                continue;
            }
            accumulator.observe(content, memory);
        }
        return accumulator.toResponse(memories.size());
    }

    private String resolveContent(MemoryEntry memory) {
        if (StringUtils.hasText(memory.getCompressedContent())) {
            return memory.getCompressedContent();
        }
        return memory.getContent();
    }

    /**
     * 用户画像提取过程中的临时累加器。
     */
    private static final class ProfileAccumulator {

        private String displayName;
        private String role;
        private String company;
        private String industry;
        private String communicationStyle;
        private String preferredFormat;
        private final Map<String, Integer> expertiseAreas = new LinkedHashMap<>();
        private final Map<String, Integer> topics = new LinkedHashMap<>();
        private final Map<String, Integer> dataSources = new LinkedHashMap<>();
        private int evidenceCount;

        private void observe(String content, MemoryEntry memory) {
            extractIdentity(content);
            extractPreference(content, memory);
            extractTopics(content);
            extractDataSource(content);
        }

        private void extractIdentity(String content) {
            displayName = firstNonBlank(displayName, firstGroup(NAME_PATTERN, content));
            company = firstNonBlank(company, firstGroup(COMPANY_PATTERN, content));
            role = firstNonBlank(role, firstGroup(ROLE_PATTERN, content));
            for (Map.Entry<String, String> entry : INDUSTRY_KEYWORDS.entrySet()) {
                if (content.contains(entry.getKey())) {
                    industry = firstNonBlank(industry, entry.getValue());
                    add(expertiseAreas, entry.getValue());
                }
            }
            if (StringUtils.hasText(displayName) || StringUtils.hasText(company) || StringUtils.hasText(role)) {
                evidenceCount++;
            }
        }

        private void extractPreference(String content, MemoryEntry memory) {
            if (content.contains("简洁") || content.contains("直接")) {
                communicationStyle = firstNonBlank(communicationStyle, "简洁直接");
                evidenceCount++;
            } else if (content.contains("详细") || content.contains("解释")) {
                communicationStyle = firstNonBlank(communicationStyle, "详细解释");
                evidenceCount++;
            }
            if (content.contains("图表") || content.contains("可视化")) {
                preferredFormat = firstNonBlank(preferredFormat, "图表优先");
                evidenceCount++;
            } else if (content.contains("表格") || content.contains("列表")) {
                preferredFormat = firstNonBlank(preferredFormat, "表格优先");
                evidenceCount++;
            }
            if (MemoryType.PREFERENCE.equals(memory.getType())) {
                add(topics, "偏好设置");
            }
        }

        private void extractTopics(String content) {
            collectKeyword(content, "销售", "销售分析");
            collectKeyword(content, "运营", "运营分析");
            collectKeyword(content, "库存", "库存分析");
            collectKeyword(content, "用户", "用户分析");
            collectKeyword(content, "报表", "报表生成");
            collectKeyword(content, "SQL", "SQL 分析");
        }

        private void extractDataSource(String content) {
            if (content.contains("MySQL") || content.contains("mysql")) {
                add(dataSources, "MySQL");
            }
            if (content.contains("Excel") || content.contains("excel")) {
                add(dataSources, "Excel");
            }
            if (content.contains("CSV") || content.contains("csv")) {
                add(dataSources, "CSV");
            }
        }

        private void collectKeyword(String content, String keyword, String label) {
            if (content.contains(keyword)) {
                add(topics, label);
                add(expertiseAreas, label);
            }
        }

        private UserMemoryProfileSnapshotResponse toResponse(int memoryCount) {
            return new UserMemoryProfileSnapshotResponse(displayName, role, company, industry,
                    communicationStyle, preferredFormat, topKeys(expertiseAreas), topKeys(topics),
                    topKeys(dataSources), calculateConfidence(memoryCount));
        }

        private double calculateConfidence(int memoryCount) {
            if (memoryCount <= 0) {
                return 0D;
            }
            double identityScore = countPresent(displayName, role, company, industry) / 4D;
            double preferenceScore = countPresent(communicationStyle, preferredFormat) / 2D;
            double evidenceScore = Math.min(1D, evidenceCount / 5D);
            return Math.round((identityScore * 0.45D + preferenceScore * 0.25D + evidenceScore * 0.3D) * 100D)
                    / 100D;
        }

        private int countPresent(String... values) {
            int count = 0;
            for (String value : values) {
                if (StringUtils.hasText(value)) {
                    count++;
                }
            }
            return count;
        }

        private String firstGroup(Pattern pattern, String content) {
            Matcher matcher = pattern.matcher(content);
            return matcher.find() ? matcher.group(1).trim() : null;
        }

        private String firstNonBlank(String current, String candidate) {
            return StringUtils.hasText(current) ? current : candidate;
        }

        private void add(Map<String, Integer> target, String value) {
            if (StringUtils.hasText(value)) {
                target.merge(value, 1, Integer::sum);
            }
        }

        private List<String> topKeys(Map<String, Integer> source) {
            List<Map.Entry<String, Integer>> entries = new ArrayList<>(source.entrySet());
            entries.sort((left, right) -> Integer.compare(right.getValue(), left.getValue()));
            return entries.stream().limit(MAX_LIST_SIZE).map(Map.Entry::getKey).toList();
        }
    }
}
