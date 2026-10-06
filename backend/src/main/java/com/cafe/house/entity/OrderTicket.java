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
 * 点单出单（账单）：引用桌台；total 自动汇总；状态（制作中/已完成/已结账）。
 */
@Entity
@Table(name = "order_ticket")
public class OrderTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "table_id")
    public Long tableId;

    @Column(name = "total", nullable = false, precision = 10, scale = 2)
    public BigDecimal total;

    @Column(name = "status", nullable = false, length = 16)
    public String status;

    /** 完成制作的操作者（收银员），消耗流水与其同源生成 */
    @Column(name = "finished_by", length = 32)
    public String finishedBy;

    /** 完成时间 = 消耗生成时间 */
    @Column(name = "finished_at")
    public LocalDateTime finishedAt;

    @Column(name = "created_at")
    public LocalDateTime createdAt = LocalDateTime.now();
}
