package com.ai.agent.eval;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.dao.OptimisticLockingFailureException;

import java.util.List;

/** 评测持久层：显式保存运行结果，抢占使用事务内行锁，更新保留版本约束。 */
@Mapper
public interface AgentEvalMapper {
    int insertRun(AgentEvalRunEntity run);
    int insertResult(AgentEvalResultEntity result);
    int updateRun(AgentEvalRunEntity run);
    AgentEvalRunEntity findRun(@Param("runId") String runId, @Param("tenantId") String tenantId);
    AgentEvalRunEntity lockRun(@Param("runId") String runId, @Param("tenantId") String tenantId);
    AgentEvalDatasetEntity findDataset(@Param("datasetId") String datasetId, @Param("tenantId") String tenantId);
    List<AgentEvalDatasetEntity> listReadyDatasets(@Param("tenantId") String tenantId,
            @Param("pageable") org.springframework.data.domain.Pageable pageable);
    long countReadyDatasets(@Param("tenantId") String tenantId);
    List<AgentEvalRunEntity> listRuns(@Param("tenantId") String tenantId,
            @Param("pageable") org.springframework.data.domain.Pageable pageable);
    long countRuns(@Param("tenantId") String tenantId);
    List<AgentEvalSampleEntity> loadSamples(@Param("datasetId") String datasetId, @Param("version") int version,
            @Param("tenantId") String tenantId);
    long countEnabledSamples(@Param("datasetId") String datasetId, @Param("version") int version,
            @Param("tenantId") String tenantId);
    List<AgentEvalResultEntity> listResults(@Param("runId") String runId, @Param("tenantId") String tenantId);

    List<AgentEvalSampleEntity> loadSamplePage(@Param("datasetId") String datasetId, @Param("version") int version,
            @Param("tenantId") String tenantId, @Param("pageable") org.springframework.data.domain.Pageable pageable);
    List<AgentEvalResultEntity> listPageResults(@Param("runId") String runId, @Param("tenantId") String tenantId,
            @Param("sampleIds") List<String> sampleIds);

    default void saveRun(AgentEvalRunEntity run) {
        if (updateRun(run) != 1) {
            throw new OptimisticLockingFailureException("评测运行已变化，请重新读取");
        }
        run.setRowVersion(run.getRowVersion() + 1);
    }
}
