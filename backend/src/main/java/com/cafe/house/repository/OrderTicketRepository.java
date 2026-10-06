package com.cafe.house.repository;

import com.cafe.house.entity.OrderTicket;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderTicketRepository extends JpaRepository<OrderTicket, Long> {

    List<OrderTicket> findByStatus(String status);

    /** 同一桌台处于「制作中 / 已完成」未结账的账单 */
    List<OrderTicket> findByTableIdAndStatusIn(Long tableId, List<String> statuses);

    /** 悲观写锁：完成制作 / 结账时锁定账单行，状态只推进一次 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from OrderTicket t where t.id = :id")
    Optional<OrderTicket> findByIdForUpdate(@Param("id") Long id);
}
