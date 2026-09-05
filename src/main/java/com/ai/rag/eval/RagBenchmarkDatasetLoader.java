package com.ai.rag.eval;

import com.ai.rag.RagProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 从固定运维路径加载并校验 RAG 黄金数据集。
 *
 * @author data-agent
 */
@Component
public class RagBenchmarkDatasetLoader {

    private static final long MAX_DATASET_BYTES = 5L * 1024L * 1024L;
    private static final int HARD_MINIMUM_CASES = 30;

    private final ObjectMapper objectMapper;
    private final RagProperties ragProperties;

    public RagBenchmarkDatasetLoader(ObjectMapper objectMapper, RagProperties ragProperties) {
        this.objectMapper = objectMapper;
        this.ragProperties = ragProperties;
    }

    /**
     * 加载当前配置的固定数据集。
     *
     * @return 数据集、规范化路径和内容摘要
     */
    public LoadedDataset load() {
        Path path = configuredPath();
        try {
            validateFile(path);
            byte[] content = Files.readAllBytes(path);
            RagBenchmarkDataset dataset = objectMapper.readValue(content, RagBenchmarkDataset.class);
            validateDataset(dataset, path);
            return new LoadedDataset(dataset, path.toString(), sha256(content));
        } catch (IOException e) {
            throw new IllegalStateException("RAG 黄金集加载失败: path=" + path + ", cause=" + e.getMessage(), e);
        }
    }

    private Path configuredPath() {
        String configured = ragProperties.getBenchmark().getDatasetPath();
        if (!StringUtils.hasText(configured)) {
            throw new IllegalStateException("RAG 黄金集路径不能为空: app.rag.benchmark.dataset-path");
        }
        return Path.of(configured.trim()).toAbsolutePath().normalize();
    }

    private void validateFile(Path path) throws IOException {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IllegalStateException("RAG 黄金集不存在或不可读: path=" + path);
        }
        long size = Files.size(path);
        if (size <= 0L || size > MAX_DATASET_BYTES) {
            throw new IllegalStateException("RAG 黄金集文件大小非法: path=" + path + ", bytes=" + size
                    + ", maxBytes=" + MAX_DATASET_BYTES);
        }
    }

    private void validateDataset(RagBenchmarkDataset dataset, Path path) {
        if (dataset == null) {
            throw invalid(path, "JSON内容为空");
        }
        requireText(dataset.datasetId(), path, "datasetId");
        requireText(dataset.corpusVersion(), path, "corpusVersion");
        List<RagBenchmarkCase> cases = dataset.cases();
        int minimumCases = ragProperties.getBenchmark().getMinimumCases();
        int maximumCases = ragProperties.getBenchmark().getMaximumCases();
        if (minimumCases < HARD_MINIMUM_CASES || maximumCases < minimumCases) {
            throw new IllegalStateException("RAG 黄金集用例数量配置非法: minimumCases=" + minimumCases
                    + ", maximumCases=" + maximumCases + ", hardMinimumCases=" + HARD_MINIMUM_CASES);
        }
        if (cases == null || cases.size() < minimumCases) {
            throw invalid(path, "cases至少需要" + minimumCases + "条，实际=" + sizeOf(cases));
        }
        if (cases.size() > maximumCases) {
            throw invalid(path, "cases最多允许" + maximumCases + "条，实际=" + cases.size());
        }
        Set<String> caseIds = new HashSet<>();
        for (int index = 0; index < cases.size(); index++) {
            validateCase(cases.get(index), index, caseIds, path);
        }
    }

    private void validateCase(RagBenchmarkCase benchmarkCase, int index, Set<String> caseIds, Path path) {
        if (benchmarkCase == null) {
            throw invalid(path, "cases[" + index + "]不能为空");
        }
        String caseId = requireText(benchmarkCase.caseId(), path, "cases[" + index + "].caseId");
        requireText(benchmarkCase.query(), path, "cases[" + index + "].query");
        if (!caseIds.add(caseId)) {
            throw invalid(path, "caseId重复: " + caseId);
        }
        boolean hasSource = hasText(benchmarkCase.expectedSourceIds());
        boolean hasChunk = hasText(benchmarkCase.expectedChunkIds());
        boolean hasGradedChunk = hasPositiveGrade(benchmarkCase.relevanceGrades());
        if (!hasSource && !hasChunk && !hasGradedChunk) {
            throw invalid(path, "用例缺少sourceId或chunkId标注: caseId=" + caseId);
        }
        validateGrades(benchmarkCase.relevanceGrades(), caseId, path);
    }

    private void validateGrades(Map<String, Integer> grades, String caseId, Path path) {
        if (grades == null) {
            return;
        }
        for (Map.Entry<String, Integer> entry : grades.entrySet()) {
            if (!StringUtils.hasText(entry.getKey()) || entry.getValue() == null || entry.getValue() < 0) {
                throw invalid(path, "relevanceGrades非法: caseId=" + caseId);
            }
        }
    }

    private String requireText(String value, Path path, String field) {
        if (!StringUtils.hasText(value)) {
            throw invalid(path, "字段不能为空: " + field);
        }
        return value.trim();
    }

    private boolean hasText(List<String> values) {
        return values != null && values.stream().anyMatch(StringUtils::hasText);
    }

    private boolean hasPositiveGrade(Map<String, Integer> grades) {
        return grades != null && grades.entrySet().stream()
                .anyMatch(entry -> StringUtils.hasText(entry.getKey())
                        && entry.getValue() != null && entry.getValue() > 0);
    }

    private int sizeOf(List<?> values) {
        return values == null ? 0 : values.size();
    }

    private IllegalStateException invalid(Path path, String reason) {
        return new IllegalStateException("RAG 黄金集校验失败: path=" + path + ", reason=" + reason);
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
    }

    /**
     * 已验证的数据集及其可复现身份。
     *
     * @param dataset 数据集内容
     * @param path 规范化文件路径
     * @param sha256 文件内容摘要
     */
    public record LoadedDataset(RagBenchmarkDataset dataset, String path, String sha256) {
    }
}
