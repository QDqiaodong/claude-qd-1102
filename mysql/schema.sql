SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET character_set_client = utf8mb4;
SET character_set_connection = utf8mb4;
SET character_set_results = utf8mb4;

-- ---------------------------------------------------------------------
-- 咖啡馆 · 咖啡豆批次 / 饮品菜单 / 桌台 / 点单出单 / 配方与消耗
-- 库名 cafe_house；ddl-auto: none，表结构全部由本文件在 MySQL 镜像构建期导入。
-- 注：mysql 容器是 UTC，时间戳统一用 UTC_TIMESTAMP() + INTERVAL 8 HOUR（北京时间）。

CREATE DATABASE IF NOT EXISTS cafe_house
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE cafe_house;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS order_consumption;
DROP TABLE IF EXISTS menu_recipe;
DROP TABLE IF EXISTS order_item;
DROP TABLE IF EXISTS order_ticket;
DROP TABLE IF EXISTS table_info;
DROP TABLE IF EXISTS menu_item;
DROP TABLE IF EXISTS bean_batch;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------- 咖啡豆批次 ----------------
-- remaining_g：批次余量（克）。入库时按 weight_kg 折算初始余量；
-- 仅在账单「完成制作」时按配方扣减，扣到 0 自动转为「已用完」。
CREATE TABLE bean_batch (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_no    VARCHAR(32) NOT NULL UNIQUE COMMENT '批次号 BB-20260901',
    origin      VARCHAR(64) NOT NULL COMMENT '产地（即配方中的豆种）',
    roast_level VARCHAR(8)  NOT NULL COMMENT '烘焙度 浅/中/深',
    weight_kg   DECIMAL(10,2) NOT NULL COMMENT '重量(kg)',
    remaining_g DECIMAL(10,1) NOT NULL DEFAULT 0 COMMENT '批次余量(克)',
    status      VARCHAR(16) NOT NULL COMMENT '待烘焙/烘焙中/已入库/已用完',
    created_at  DATETIME    COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 饮品菜单 ----------------
CREATE TABLE menu_item (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    name     VARCHAR(64) NOT NULL UNIQUE COMMENT '饮品名',
    price    DECIMAL(10,2) NOT NULL COMMENT '价格',
    category VARCHAR(16)  NOT NULL COMMENT '类别 意式/手冲/非咖啡',
    status   VARCHAR(16)  NOT NULL COMMENT '在售/下架',
    created_at DATETIME   COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 饮品配方 ----------------
-- 每行：某饮品每杯需要某豆种（= 批次产地）多少克。
-- 只影响保存之后完成的账单；已完成的账单消耗以 order_consumption 快照为准。
CREATE TABLE menu_recipe (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    menu_item_id BIGINT      NOT NULL COMMENT '饮品 id',
    bean_origin  VARCHAR(64) NOT NULL COMMENT '豆种（= 批次产地）',
    grams        DECIMAL(10,1) NOT NULL COMMENT '每杯用量(克)',
    UNIQUE KEY uk_menu_origin (menu_item_id, bean_origin)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 桌台 ----------------
CREATE TABLE table_info (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_no VARCHAR(16) NOT NULL UNIQUE COMMENT '桌号 A1',
    seats    INT         NOT NULL COMMENT '座位数',
    status   VARCHAR(16) NOT NULL COMMENT '空闲/占用/清洁中',
    created_at DATETIME  COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 点单出单（账单） ----------------
CREATE TABLE order_ticket (
    id       BIGINT AUTO_INCREMENT PRIMARY KEY,
    table_id BIGINT       COMMENT '桌台 id',
    total    DECIMAL(10,2) NOT NULL COMMENT '合计金额(自动汇总)',
    status   VARCHAR(16) NOT NULL COMMENT '制作中/已完成/已结账',
    finished_by VARCHAR(32) COMMENT '完成制作的 operator（收银员）',
    finished_at DATETIME    COMMENT '完成时间（消耗生成时间）',
    created_at DATETIME  COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 账单明细 ----------------
-- menu_name / unit_price 是加单时刻的快照：之后饮品改名、调价都不影响历史账单。
CREATE TABLE order_item (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id    BIGINT NOT NULL COMMENT '所属账单 id',
    menu_item_id BIGINT NOT NULL COMMENT '饮品 id',
    menu_name    VARCHAR(64) NOT NULL COMMENT '饮品名快照',
    unit_price   DECIMAL(10,2) NOT NULL COMMENT '单价快照',
    qty          INT    NOT NULL COMMENT '数量'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 咖啡豆消耗流水 ----------------
-- 账单「完成制作」时按当时配方生成，整单全量满足才落库，否则一行都没有。
-- 每行 = 某账单明细从某批次扣了多少克；批次号/豆种/操作者/时间全部快照。
CREATE TABLE order_consumption (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id     BIGINT NOT NULL COMMENT '账单 id',
    order_item_id BIGINT NOT NULL COMMENT '账单明细 id',
    menu_item_id  BIGINT NOT NULL COMMENT '饮品 id',
    menu_name     VARCHAR(64) NOT NULL COMMENT '饮品名快照',
    batch_id      BIGINT NOT NULL COMMENT '批次 id',
    batch_no      VARCHAR(32) NOT NULL COMMENT '批次号快照',
    bean_origin   VARCHAR(64) NOT NULL COMMENT '豆种快照',
    grams         DECIMAL(10,1) NOT NULL COMMENT '扣减克数',
    operator      VARCHAR(32) NOT NULL COMMENT '操作者（收银员）',
    created_at    DATETIME COMMENT '消耗时间',
    UNIQUE KEY uk_ticket_item_batch (ticket_id, order_item_id, batch_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- 种子数据
-- =====================================================================

INSERT INTO bean_batch (batch_no, origin, roast_level, weight_kg, remaining_g, status, created_at) VALUES
('BB-20260901', '埃塞俄比亚 耶加雪菲', '浅', 10.00,     0.0, '待烘焙', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 50 DAY),
('BB-20260905', '哥伦比亚 慧兰',     '中', 12.50,     0.0, '烘焙中', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 40 DAY),
('BB-20260910', '巴西 喜拉多',       '深', 15.00, 14964.0, '已入库', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 30 DAY),
('BB-20260912', '危地马拉 安提瓜',   '中',  8.00,  8000.0, '已入库', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 20 DAY),
('BB-20260915', '印尼 曼特宁',       '深', 20.00,     0.0, '已用完', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 10 DAY),
('BB-20260920', '肯尼亚 AA',         '浅',  6.00,     0.0, '待烘焙', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 5 DAY);

INSERT INTO menu_item (name, price, category, status, created_at) VALUES
('意式浓缩',     18.00, '意式',   '在售', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('拿铁',         28.00, '意式',   '在售', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('卡布奇诺',     26.00, '意式',   '在售', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('美式',         22.00, '意式',   '在售', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('手冲 耶加雪菲', 38.00, '手冲',   '在售', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('手冲 曼特宁',   42.00, '手冲',   '在售', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('抹茶拿铁',     30.00, '非咖啡', '在售', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('气泡柚子',     25.00, '非咖啡', '下架', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY);

-- 配方：豆种 = 批次产地。耶加雪菲批次仍「待烘焙」、曼特宁已用完，
-- 用来演示点单时的库存不足提示。
INSERT INTO menu_recipe (menu_item_id, bean_origin, grams) VALUES
(1, '巴西 喜拉多', 18.0),   -- 意式浓缩
(2, '巴西 喜拉多', 18.0),   -- 拿铁
(3, '巴西 喜拉多', 18.0),   -- 卡布奇诺
(4, '巴西 喜拉多', 15.0),   -- 美式
(5, '埃塞俄比亚 耶加雪菲', 15.0),  -- 手冲 耶加雪菲（批次未入库 → 库存不足）
(6, '印尼 曼特宁', 15.0);          -- 手冲 曼特宁（批次已用完 → 库存不足）

INSERT INTO table_info (table_no, seats, status, created_at) VALUES
('A1', 2, '空闲',   UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('A2', 4, '占用',   UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('B1', 2, '空闲',   UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('B2', 6, '清洁中', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('C1', 4, '占用',   UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('C2', 2, '空闲',   UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY);

-- 账单：A2(id=2) 制作中、C1(id=5) 已完成、A1(id=1) 已结账（桌台已释放为空闲）
INSERT INTO order_ticket (id, table_id, total, status, finished_by, finished_at, created_at) VALUES
(1, 2, 54.00, '制作中', NULL, NULL, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY),
(2, 5, 38.00, '已完成', '小王', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 2 DAY, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 2 DAY),
(3, 1, 46.00, '已结账', '小王', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 3 DAY, UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 3 DAY);

INSERT INTO order_item (ticket_id, menu_item_id, menu_name, unit_price, qty) VALUES
(1, 2, '拿铁',         28.00, 1),
(1, 3, '卡布奇诺',     26.00, 1),
(2, 5, '手冲 耶加雪菲', 38.00, 1),
(3, 1, '意式浓缩',     18.00, 1),
(3, 2, '拿铁',         28.00, 1);

-- 历史消耗快照：账单 3（已结账）当时从 BB-20260910 扣了 意式浓缩 18g + 拿铁 18g，
-- 与 BB-20260910 的余量 15000-36=14964 对应。账单 2 完成于本功能上线前，无消耗记录。
INSERT INTO order_consumption (ticket_id, order_item_id, menu_item_id, menu_name, batch_id, batch_no, bean_origin, grams, operator, created_at) VALUES
(3, 4, 1, '意式浓缩', 3, 'BB-20260910', '巴西 喜拉多', 18.0, '小王', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 3 DAY),
(3, 5, 2, '拿铁',     3, 'BB-20260910', '巴西 喜拉多', 18.0, '小王', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 3 DAY);
