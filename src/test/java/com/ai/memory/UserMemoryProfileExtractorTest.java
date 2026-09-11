package com.ai.memory;

import com.ai.memory.dto.UserMemoryProfileSnapshotResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserMemoryProfileExtractorTest {

    private final UserMemoryProfileExtractor extractor = new UserMemoryProfileExtractor();

    @Test
    void shouldProjectNewestStructuredSemanticValues() {
        MemoryEntry latestFormat = memory(MemoryType.PREFERENCE,
                SemanticMemoryKey.OUTPUT_FORMAT, "列表优先", 0.9D, MemorySource.USER_IMPLICIT);
        MemoryEntry oldFormat = memory(MemoryType.PREFERENCE,
                SemanticMemoryKey.OUTPUT_FORMAT, "表格优先", 1D, MemorySource.USER_EXPLICIT);
        MemoryEntry role = memory(MemoryType.ENTITY,
                SemanticMemoryKey.ROLE, "Java 工程师", 1D, MemorySource.USER_EXPLICIT);

        UserMemoryProfileSnapshotResponse profile = extractor.extract(List.of(latestFormat, oldFormat, role));

        assertThat(profile.preferredFormat()).isEqualTo("列表优先");
        assertThat(profile.role()).isEqualTo("Java 工程师");
        assertThat(profile.confidence()).isEqualTo(0.97D);
    }

    @Test
    void shouldIgnoreSummariesAndUnknownKeysWhenBuildingProfile() {
        MemoryEntry misleadingSummary = memory(MemoryType.SUMMARY,
                SemanticMemoryKey.ROLE, "你的", 1D, MemorySource.SYSTEM_GENERATED);
        MemoryEntry genericFact = memory(MemoryType.ENTITY,
                "profile.general.legacy", "用户提到一个项目", 1D, MemorySource.USER_EXPLICIT);

        UserMemoryProfileSnapshotResponse profile = extractor.extract(List.of(misleadingSummary, genericFact));

        assertThat(profile.role()).isNull();
        assertThat(profile.confidence()).isZero();
        assertThat(profile.evidenceCount()).isZero();
    }

    @Test
    void shouldCollectKnownListKeysWithoutDuplicates() {
        MemoryEntry first = memory(MemoryType.ENTITY,
                "profile.expertise_area.java", "Java", 0.9D, MemorySource.USER_IMPLICIT);
        MemoryEntry duplicate = memory(MemoryType.ENTITY,
                "profile.expertise_area.backend", "Java", 0.9D, MemorySource.USER_IMPLICIT);

        UserMemoryProfileSnapshotResponse profile = extractor.extract(List.of(first, duplicate));

        assertThat(profile.expertiseAreas()).containsExactly("Java");
    }

    private MemoryEntry memory(MemoryType type, String semanticKey, String content,
            double confidence, MemorySource source) {
        MemoryEntry entry = new MemoryEntry();
        entry.setType(type);
        entry.setSemanticKey(semanticKey);
        entry.setCompressedContent(content);
        entry.setConfidence(confidence);
        entry.setSource(source);
        return entry;
    }
}
