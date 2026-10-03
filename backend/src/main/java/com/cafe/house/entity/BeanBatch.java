package com.cafe.house.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 咖啡豆批次：烘焙状态机（待烘焙→烘焙中→已入库→已用完）。
 */
@Entity
@Table(name = "bean_batch")
public class BeanBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "batch_no", nullable = false, unique = true, length = 32)
    public String batchNo;

    @Column(name = "origin", nullable = false, length = 64)
    public String origin;

    @Column(name = "roast_level", nullable = false, length = 8)
    public String roastLevel;

    @Column(name = "weight_kg", nullable = false, precision = 10, scale = 2)
    public BigDecimal weightKg;

    @Column(name = "status", nullable = false, length = 16)
    public String status;

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();
}
