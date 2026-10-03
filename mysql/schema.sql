SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;
SET character_set_client = utf8mb4;
SET character_set_connection = utf8mb4;
SET character_set_results = utf8mb4;

-- ---------------------------------------------------------------------
-- 咖啡馆 · 咖啡豆批次 / 饮品菜单 / 桌台 / 点单出单
-- 库名 cafe_house；ddl-auto: none，表结构全部由本文件在 MySQL 镜像构建期导入。
-- 注：mysql 容器是 UTC，时间戳统一用 UTC_TIMESTAMP() + INTERVAL 8 HOUR（北京时间）。

CREATE DATABASE IF NOT EXISTS cafe_house
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE cafe_house;

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS order_item;
DROP TABLE IF EXISTS order_ticket;
DROP TABLE IF EXISTS table_info;
DROP TABLE IF EXISTS menu_item;
DROP TABLE IF EXISTS bean_batch;
SET FOREIGN_KEY_CHECKS = 1;

-- ---------------- 咖啡豆批次 ----------------
CREATE TABLE bean_batch (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    batch_no    VARCHAR(32) NOT NULL UNIQUE COMMENT '批次号 BB-20260901',
    origin      VARCHAR(64) NOT NULL COMMENT '产地',
    roast_level VARCHAR(8)  NOT NULL COMMENT '烘焙度 浅/中/深',
    weight_kg   DECIMAL(10,2) NOT NULL COMMENT '重量(kg)',
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
    created_at DATETIME  COMMENT '创建时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------- 账单明细 ----------------
CREATE TABLE order_item (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    ticket_id    BIGINT NOT NULL COMMENT '所属账单 id',
    menu_item_id BIGINT NOT NULL COMMENT '饮品 id',
    qty          INT    NOT NULL COMMENT '数量'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- =====================================================================
-- 种子数据
-- =====================================================================

INSERT INTO bean_batch (batch_no, origin, roast_level, weight_kg, status, created_at) VALUES
('BB-20260901', '埃塞俄比亚 耶加雪菲', '浅', 10.00, '待烘焙', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 50 DAY),
('BB-20260905', '哥伦比亚 慧兰',     '中', 12.50, '烘焙中', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 40 DAY),
('BB-20260910', '巴西 喜拉多',       '深', 15.00, '已入库', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 30 DAY),
('BB-20260912', '危地马拉 安提瓜',   '中',  8.00, '已入库', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 20 DAY),
('BB-20260915', '印尼 曼特宁',       '深', 20.00, '已用完', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 10 DAY),
('BB-20260920', '肯尼亚 AA',         '浅',  6.00, '待烘焙', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 5 DAY);

INSERT INTO menu_item (name, price, category, status, created_at) VALUES
('意式浓缩',     18.00, '意式',   '在售', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('拿铁',         28.00, '意式',   '在售', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('卡布奇诺',     26.00, '意式',   '在售', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('美式',         22.00, '意式',   '在售', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('手冲 耶加雪菲', 38.00, '手冲',   '在售', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('手冲 曼特宁',   42.00, '手冲',   '在售', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('抹茶拿铁',     30.00, '非咖啡', '在售', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('气泡柚子',     25.00, '非咖啡', '下架', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY);

INSERT INTO table_info (table_no, seats, status, created_at) VALUES
('A1', 2, '空闲',   UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('A2', 4, '占用',   UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('B1', 2, '空闲',   UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('B2', 6, '清洁中', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('C1', 4, '占用',   UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY),
('C2', 2, '空闲',   UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 60 DAY);

-- 账单：A2(id=2) 制作中、C1(id=5) 已完成、A1(id=1) 已结账（桌台已释放为空闲）
INSERT INTO order_ticket (id, table_id, total, status, created_at) VALUES
(1, 2, 54.00, '制作中', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 1 DAY),
(2, 5, 38.00, '已完成', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 2 DAY),
(3, 1, 46.00, '已结账', UTC_TIMESTAMP() + INTERVAL 8 HOUR - INTERVAL 3 DAY);

INSERT INTO order_item (ticket_id, menu_item_id, qty) VALUES
(1, 2, 1),  -- 拿铁 28
(1, 3, 1),  -- 卡布奇诺 26 => 54
(2, 5, 1),  -- 手冲 耶加雪菲 38 => 38
(3, 1, 1),  -- 意式浓缩 18
(3, 2, 1);  -- 拿铁 28 => 46
