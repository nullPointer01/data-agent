package com.ai.memory;

import com.ai.memory.dto.UserMemoryProfileSnapshotResponse;
import com.ai.repository.UserProfileMemoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserProfileMemoryServiceTest {

    @Test
    void upsertSnapshotMergesProfileAndLists() {
        UserProfileMemoryRepository repository = mock(UserProfileMemoryRepository.class);
        MemoryJsonCodec codec = new MemoryJsonCodec(new ObjectMapper());
        UserProfileMemory existing = new UserProfileMemory();
        existing.setTenantId("tenant-1");
        existing.setUserId("user-1");
        existing.setExpertiseAreasJson(codec.toJson(List.of("电商")));
        existing.setConfidence(0.4D);
        when(repository.findByTenantIdAndUserId("tenant-1", "user-1")).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        UserProfileMemoryService service = new UserProfileMemoryService(repository, codec);
        UserMemoryProfileSnapshotResponse incoming = new UserMemoryProfileSnapshotResponse(
                "张三", "运营", null, "电商", "简洁直接", "表格优先",
                List.of("电商", "销售分析"), List.of("报表生成"), List.of("MySQL"), 0.8D);

        UserMemoryProfileSnapshotResponse saved = service.upsertSnapshot("tenant-1", "user-1", incoming, 3);

        assertEquals("张三", saved.displayName());
        assertEquals("运营", saved.role());
        assertEquals(List.of("电商", "销售分析"), saved.expertiseAreas());
        assertEquals(0.8D, saved.confidence());
    }

    @Test
    void getSnapshotReturnsEmptyWhenProfileMissing() {
        UserProfileMemoryRepository repository = mock(UserProfileMemoryRepository.class);
        when(repository.findByTenantIdAndUserId("tenant-1", "user-1")).thenReturn(Optional.empty());
        UserProfileMemoryService service = new UserProfileMemoryService(repository,
                new MemoryJsonCodec(new ObjectMapper()));

        UserMemoryProfileSnapshotResponse snapshot = service.getSnapshot("tenant-1", "user-1");

        assertEquals(0D, snapshot.confidence());
        assertEquals(List.of(), snapshot.expertiseAreas());
    }
}
