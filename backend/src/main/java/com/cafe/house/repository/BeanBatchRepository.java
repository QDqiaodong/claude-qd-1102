package com.cafe.house.repository;

import com.cafe.house.entity.BeanBatch;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BeanBatchRepository extends JpaRepository<BeanBatch, Long> {

    boolean existsByBatchNo(String batchNo);

    List<BeanBatch> findByStatus(String status);
}
