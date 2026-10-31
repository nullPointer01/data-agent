package com.ai.repository;

import com.ai.model.DataSourceConfig;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** DataSourceConfig 的 MyBatis 仓储，SQL 见同名 Mapper XML。 */
@Mapper
public interface DataSourceConfigRepository {

    /** 明确保存生命周期；MyBatis 写入立即执行，不依赖 ORM 脏检查。 */
    @Transactional
    default DataSourceConfig save(DataSourceConfig entity) {
        boolean creating = entity.getDatasourceId() == null || !existsById(entity.getDatasourceId());
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

    int insert(DataSourceConfig entity);
    int update(DataSourceConfig entity);
    Optional<DataSourceConfig> findById(@Param("id") String id);
    boolean existsById(@Param("id") String id);
    List<DataSourceConfig> findAll();
    List<DataSourceConfig> findAllById(@Param("ids") Collection<String> ids);
    void deleteById(@Param("id") String id);

    default void delete(DataSourceConfig entity) {
        deleteById(entity.getDatasourceId());
    }

    @Transactional
    default List<DataSourceConfig> saveAll(Iterable<DataSourceConfig> entities) {
        List<DataSourceConfig> saved = new ArrayList<>();
        entities.forEach(entity -> saved.add(save(entity)));
        return saved;
    }

    List<DataSourceConfig> findByTenantIdAndEnabledTrue(@Param("tenantId") String tenantId);

    Optional<DataSourceConfig> findByDatasourceIdAndTenantId(@Param("datasourceId") String datasourceId, @Param("tenantId") String tenantId);
    /** 在数据库内完成搜索、作用域过滤和分页；计数与列表共用条件。 */
    List<DataSourceConfig> findListPage(@Param("filter") com.ai.api.CatalogListFilter filter,
            @Param("pageable") org.springframework.data.domain.Pageable pageable);
    long countList(@Param("filter") com.ai.api.CatalogListFilter filter);
}
