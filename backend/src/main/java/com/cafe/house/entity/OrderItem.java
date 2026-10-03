package com.cafe.house.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 账单明细：每条记录属于某账单、指向某饮品、带数量。
 */
@Entity
@Table(name = "order_item")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "ticket_id", nullable = false)
    public Long ticketId;

    @Column(name = "menu_item_id", nullable = false)
    public Long menuItemId;

    @Column(name = "qty", nullable = false)
    public Integer qty;
}
