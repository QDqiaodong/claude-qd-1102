package com.cafe.house.repository;

import com.cafe.house.entity.BeanBatch;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface BeanBatchRepository extends JpaRepository<BeanBatch, Long> {

    boolean existsByBatchNo(String batchNo);

    List<BeanBatch> findByStatus(String status);

    /** 某豆种当前可用余量（已入库批次的 remaining_g 合计），点单提示用 */
    @Query("select coalesce(sum(b.remainingG), 0) from BeanBatch b where b.origin = :origin and b.status = '已入库'")
    BigDecimal sumAvailableByOrigin(@Param("origin") String origin);

    /** 全部出现过的豆种（产地），配方维护的下拉选项 */
    @Query("select distinct b.origin from BeanBatch b order by b.origin")
    List<String> findDistinctOrigins();

    /**
     * 可分配批次：已入库且有余量，按最早入库（created_at）排序。
     * 悲观写锁：并发完成账单时串行扣减，余量不会被超扣。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from BeanBatch b where b.origin = :origin and b.status = '已入库' and b.remainingG > 0 "
            + "order by b.createdAt asc, b.id asc")
    List<BeanBatch> findAllocatableForUpdate(@Param("origin") String origin);
}
