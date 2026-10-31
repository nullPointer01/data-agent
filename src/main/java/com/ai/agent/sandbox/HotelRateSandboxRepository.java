package com.ai.agent.sandbox;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** HotelRateSandboxEntity 的 MyBatis 仓储，SQL 见同名 Mapper XML。 */
@Mapper
public interface HotelRateSandboxRepository {

    /** 明确保存生命周期；MyBatis 写入立即执行，不依赖 ORM 脏检查。 */
    @Transactional
    default HotelRateSandboxEntity save(HotelRateSandboxEntity entity) {
        boolean creating = entity.getId() == null || !existsById(entity.getId());
        if (creating) {
            entity.onCreate();
            insert(entity);
        } else {
            if (update(entity) != 1) {
                throw new OptimisticLockingFailureException("记录已变化，请重新读取后保存");
            }
        }
        return entity;
    }

    int insert(HotelRateSandboxEntity entity);
    int update(HotelRateSandboxEntity entity);
    Optional<HotelRateSandboxEntity> findById(@Param("id") Long id);
    boolean existsById(@Param("id") Long id);
    List<HotelRateSandboxEntity> findAll();
    List<HotelRateSandboxEntity> findAllById(@Param("ids") Collection<Long> ids);
    void deleteById(@Param("id") Long id);

    default void delete(HotelRateSandboxEntity entity) {
        deleteById(entity.getId());
    }

    @Transactional
    default List<HotelRateSandboxEntity> saveAll(Iterable<HotelRateSandboxEntity> entities) {
        List<HotelRateSandboxEntity> saved = new ArrayList<>();
        entities.forEach(entity -> saved.add(save(entity)));
        return saved;
    }

    Optional<HotelRateSandboxEntity> findByTenantIdAndActionKey(@Param("tenantId") String tenantId, @Param("actionKey") String actionKey);

    Optional<HotelRateSandboxEntity> findFirstByTenantIdAndHotelIdAndRoomTypeAndStayDateOrderByCreatedAtDesc(@Param("tenantId") String tenantId,
            @Param("hotelId") String hotelId,
            @Param("roomType") String roomType,
            @Param("stayDate") LocalDate stayDate);

    int insertOnce(@Param("tenantId") String tenantId,
            @Param("hotelId") String hotelId,
            @Param("roomType") String roomType,
            @Param("stayDate") LocalDate stayDate,
            @Param("currency") String currency,
            @Param("beforePrice") BigDecimal beforePrice,
            @Param("afterPrice") BigDecimal afterPrice,
            @Param("actionKey") String actionKey,
            @Param("approvalId") String approvalId,
            @Param("toolCallId") String toolCallId,
            @Param("createdAt") Instant createdAt);
}
