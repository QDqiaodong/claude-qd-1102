package com.cafe.house.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * 饮品配方：每行表示某饮品每杯需要某豆种（= 批次产地）多少克。
 * 配方只影响保存之后完成的账单；已完成账单的消耗以 order_consumption 快照为准，
 * 管理员改配方不会重算历史账单。
 */
@Entity
@Table(name = "menu_recipe")
public class MenuRecipe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "menu_item_id", nullable = false)
    public Long menuItemId;

    /** 豆种：与 bean_batch.origin 对应，分配时按产地匹配可用品批次 */
    @Column(name = "bean_origin", nullable = false, length = 64)
    public String beanOrigin;

    /** 每杯用量（克） */
    @Column(name = "grams", nullable = false, precision = 10, scale = 1)
    public BigDecimal grams;
}
