package com.cafe.house.repository;

import com.cafe.house.entity.OrderTicket;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderTicketRepository extends JpaRepository<OrderTicket, Long> {

    List<OrderTicket> findByStatus(String status);

    /** 同一桌台处于「制作中 / 已完成」未结账的账单 */
    List<OrderTicket> findByTableIdAndStatusIn(Long tableId, List<String> statuses);
}
