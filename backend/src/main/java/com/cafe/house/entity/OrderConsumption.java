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
 * 咖啡豆消耗流水：账单「完成制作」时按当时配方生成，生成后不再变化。
 * 批次号 / 豆种 / 饮品名 / 操作者 / 时间全部快照，供批次追溯与账单详情展示。
 */
@Entity
@Table(name = "order_consumption")
public class OrderConsumption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "ticket_id", nullable = false)
    public Long ticketId;

    @Column(name = "order_item_id", nullable = false)
    public Long orderItemId;

    @Column(name = "menu_item_id", nullable = false)
    public Long menuItemId;

    @Column(name = "menu_name", nullable = false, length = 64)
    public String menuName;

    @Column(name = "batch_id", nullable = false)
    public Long batchId;

    @Column(name = "batch_no", nullable = false, length = 32)
    public String batchNo;

    @Column(name = "bean_origin", nullable = false, length = 64)
    public String beanOrigin;

    @Column(name = "grams", nullable = false, precision = 10, scale = 1)
    public BigDecimal grams;

    @Column(name = "operator", nullable = false, length = 32)
    public String operator;

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();
}
