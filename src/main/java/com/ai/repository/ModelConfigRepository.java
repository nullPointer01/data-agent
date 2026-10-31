package com.ai.repository;

import com.ai.model.ModelConfig;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** ModelConfig 的 MyBatis 仓储，SQL 见同名 Mapper XML。 */
@Mapper
public interface ModelConfigRepository {

    /** 明确保存生命周期；MyBatis 写入立即执行，不依赖 ORM 脏检查。 */
    @Transactional
    default ModelConfig save(ModelConfig entity) {
        boolean creating = entity.getModelId() == null || !existsById(entity.getModelId());
        if (creating) {
            entity.onCreate();
            insert(entity);
        } else {
            entity.onUpdate();
            if (update(entity) != 1) {
                throw new OptimisticLockingFailureException("记录已变化，请重新读取后保存");
            }
        }
        return entity;
    }

    int insert(ModelConfig entity);
    int update(ModelConfig entity);
    Optional<ModelConfig> findById(@Param("id") String id);
    boolean existsById(@Param("id") String id);
    List<ModelConfig> findAll();
    List<ModelConfig> findAllById(@Param("ids") Collection<String> ids);
    void deleteById(@Param("id") String id);

    default void delete(ModelConfig entity) {
        deleteById(entity.getModelId());
    }

    @Transactional
    default List<ModelConfig> saveAll(Iterable<ModelConfig> entities) {
        List<ModelConfig> saved = new ArrayList<>();
        entities.forEach(entity -> saved.add(save(entity)));
        return saved;
    }

    List<ModelConfig> findByTenantIdAndEnabledTrue(@Param("tenantId") String tenantId);

    List<ModelConfig> findByTenantIdAndEnabledTrueAndIsDefaultTrue(@Param("tenantId") String tenantId);

    Optional<ModelConfig> findByModelIdAndTenantId(@Param("modelId") String modelId, @Param("tenantId") String tenantId);

    long countByIsDefaultTrue();

    int clearOtherDefaults(@Param("tenantId") String tenantId, @Param("excludedModelId") String excludedModelId);

    List<ModelConfig> findByEnabledTrue();
    /** 在数据库内完成搜索、作用域过滤和分页；计数与列表共用条件。 */
    List<ModelConfig> findListPage(@Param("filter") com.ai.api.CatalogListFilter filter,
            @Param("pageable") org.springframework.data.domain.Pageable pageable);
    long countList(@Param("filter") com.ai.api.CatalogListFilter filter);
    /** 个人选择器只读最小字段，不触发 API Key 解密。 */
    List<com.ai.modelconfig.dto.PersonalModelOptionResponse> findPersonalOptions(
            @Param("tenantId") String tenantId, @Param("query") String query,
            @Param("pageable") org.springframework.data.domain.Pageable pageable);
    long countPersonalOptions(@Param("tenantId") String tenantId, @Param("query") String query);
}
