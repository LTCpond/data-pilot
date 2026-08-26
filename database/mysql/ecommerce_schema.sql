CREATE TABLE IF NOT EXISTS users (
    id BIGINT UNSIGNED NOT NULL,
    username VARCHAR(64) NOT NULL,
    province VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    KEY idx_users_province (province)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='用户表';

CREATE TABLE IF NOT EXISTS shops (
    id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(128) NOT NULL,
    province VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_shops_province (province),
    KEY idx_shops_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='店铺表';

CREATE TABLE IF NOT EXISTS products (
    id BIGINT UNSIGNED NOT NULL,
    shop_id BIGINT UNSIGNED NOT NULL,
    name VARCHAR(128) NOT NULL,
    category VARCHAR(64) NOT NULL,
    price DECIMAL(12, 2) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    KEY idx_products_shop_id (shop_id),
    KEY idx_products_category (category),
    CONSTRAINT fk_products_shop FOREIGN KEY (shop_id) REFERENCES shops (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='商品表';

CREATE TABLE IF NOT EXISTS orders (
    id BIGINT UNSIGNED NOT NULL,
    order_no VARCHAR(32) NOT NULL,
    user_id BIGINT UNSIGNED NOT NULL,
    shop_id BIGINT UNSIGNED NOT NULL,
    status VARCHAR(16) NOT NULL,
    total_amount DECIMAL(14, 2) NOT NULL,
    paid_at DATETIME NULL,
    created_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_orders_order_no (order_no),
    KEY idx_orders_user_id (user_id),
    KEY idx_orders_shop_created (shop_id, created_at),
    KEY idx_orders_status_created (status, created_at),
    CONSTRAINT fk_orders_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_orders_shop FOREIGN KEY (shop_id) REFERENCES shops (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='订单表';

CREATE TABLE IF NOT EXISTS order_items (
    id BIGINT UNSIGNED NOT NULL,
    order_id BIGINT UNSIGNED NOT NULL,
    product_id BIGINT UNSIGNED NOT NULL,
    quantity INT UNSIGNED NOT NULL,
    unit_price DECIMAL(12, 2) NOT NULL,
    amount DECIMAL(14, 2) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_items_order_id (order_id),
    KEY idx_order_items_product_id (product_id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_items_product FOREIGN KEY (product_id) REFERENCES products (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='订单明细表';

ALTER TABLE users
    MODIFY id BIGINT UNSIGNED NOT NULL COMMENT '用户ID',
    MODIFY username VARCHAR(64) NOT NULL COMMENT '用户名',
    MODIFY province VARCHAR(32) NOT NULL COMMENT '所在省份',
    MODIFY created_at DATETIME NOT NULL COMMENT '创建时间';

ALTER TABLE shops
    MODIFY id BIGINT UNSIGNED NOT NULL COMMENT '店铺ID',
    MODIFY name VARCHAR(128) NOT NULL COMMENT '店铺名称',
    MODIFY province VARCHAR(32) NOT NULL COMMENT '所在省份',
    MODIFY status VARCHAR(16) NOT NULL COMMENT '店铺状态：ACTIVE/INACTIVE',
    MODIFY created_at DATETIME NOT NULL COMMENT '创建时间';

ALTER TABLE products
    MODIFY id BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
    MODIFY shop_id BIGINT UNSIGNED NOT NULL COMMENT '所属店铺ID',
    MODIFY name VARCHAR(128) NOT NULL COMMENT '商品名称',
    MODIFY category VARCHAR(64) NOT NULL COMMENT '商品分类',
    MODIFY price DECIMAL(12, 2) NOT NULL COMMENT '商品单价',
    MODIFY status VARCHAR(16) NOT NULL COMMENT '商品状态：ON_SALE/OFF_SALE',
    MODIFY created_at DATETIME NOT NULL COMMENT '创建时间';

ALTER TABLE orders
    MODIFY id BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
    MODIFY order_no VARCHAR(32) NOT NULL COMMENT '订单编号',
    MODIFY user_id BIGINT UNSIGNED NOT NULL COMMENT '下单用户ID',
    MODIFY shop_id BIGINT UNSIGNED NOT NULL COMMENT '所属店铺ID',
    MODIFY status VARCHAR(16) NOT NULL COMMENT '订单状态：COMPLETED/CANCELLED/REFUNDED',
    MODIFY total_amount DECIMAL(14, 2) NOT NULL COMMENT '订单总金额',
    MODIFY paid_at DATETIME NULL COMMENT '支付时间',
    MODIFY created_at DATETIME NOT NULL COMMENT '创建时间';

ALTER TABLE order_items
    MODIFY id BIGINT UNSIGNED NOT NULL COMMENT '明细ID',
    MODIFY order_id BIGINT UNSIGNED NOT NULL COMMENT '订单ID',
    MODIFY product_id BIGINT UNSIGNED NOT NULL COMMENT '商品ID',
    MODIFY quantity INT UNSIGNED NOT NULL COMMENT '购买数量',
    MODIFY unit_price DECIMAL(12, 2) NOT NULL COMMENT '成交单价',
    MODIFY amount DECIMAL(14, 2) NOT NULL COMMENT '明细金额';
