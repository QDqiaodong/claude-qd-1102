package com.cafe.house.repository;

import com.cafe.house.entity.OrderConsumption;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderConsumptionRepository extends JpaRepository<OrderConsumption, Long> {

    List<OrderConsumption> findByTicketIdOrderById(Long ticketId);

    /** 批次追溯：该批次被哪些账单消耗过 */
    List<OrderConsumption> findByBatchIdOrderById(Long batchId);
}
