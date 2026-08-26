DROP PROCEDURE IF EXISTS seed_ecommerce_demo;

DELIMITER $$

CREATE PROCEDURE seed_ecommerce_demo()
BEGIN
    DECLARE i INT DEFAULT 1;
    DECLARE first_product_id INT;
    DECLARE second_product_id INT;
    DECLARE first_quantity INT;
    DECLARE second_quantity INT;
    DECLARE first_price DECIMAL(12, 2);
    DECLARE second_price DECIMAL(12, 2);
    DECLARE order_time DATETIME;
    DECLARE order_status VARCHAR(16);
    DECLARE order_total DECIMAL(14, 2);

    WHILE i <= 20 DO
        INSERT IGNORE INTO users (id, username, province, created_at)
        VALUES (
            i,
            CONCAT('用户', LPAD(i, 2, '0')),
            ELT(MOD(i - 1, 5) + 1, '浙江', '江苏', '广东', '四川', '湖北'),
            DATE_SUB('2026-08-01 09:00:00', INTERVAL i DAY)
        );
        SET i = i + 1;
    END WHILE;

    SET i = 1;
    WHILE i <= 5 DO
        INSERT IGNORE INTO shops (id, name, province, status, created_at)
        VALUES (
            i,
            CONCAT(ELT(i, '杭州', '南京', '深圳', '成都', '武汉'), '示范店'),
            ELT(i, '浙江', '江苏', '广东', '四川', '湖北'),
            'ACTIVE',
            DATE_SUB('2026-01-01 09:00:00', INTERVAL i MONTH)
        );
        SET i = i + 1;
    END WHILE;

    SET i = 1;
    WHILE i <= 20 DO
        INSERT IGNORE INTO products (id, shop_id, name, category, price, status, created_at)
        VALUES (
            i,
            MOD(i - 1, 5) + 1,
            CONCAT(ELT(MOD(i - 1, 4) + 1, '数码商品', '家居用品', '休闲零食', '运动装备'), LPAD(i, 2, '0')),
            ELT(MOD(i - 1, 4) + 1, '数码', '家居', '食品', '运动'),
            10.00 + i * 7.50,
            'ON_SALE',
            DATE_SUB('2026-06-01 09:00:00', INTERVAL i DAY)
        );
        SET i = i + 1;
    END WHILE;

    SET i = 1;
    WHILE i <= 60 DO
        SET first_product_id = MOD(i - 1, 20) + 1;
        SET second_product_id = MOD(i + 4, 20) + 1;
        SET first_quantity = MOD(i, 3) + 1;
        SET second_quantity = MOD(i + 1, 2) + 1;
        SET first_price = 10.00 + first_product_id * 7.50;
        SET second_price = 10.00 + second_product_id * 7.50;
        SET order_total = first_price * first_quantity + second_price * second_quantity;
        SET order_time = DATE_SUB('2026-08-12 12:00:00', INTERVAL (60 - i) DAY);
        SET order_status = CASE
            WHEN MOD(i, 10) = 0 THEN 'REFUNDED'
            WHEN MOD(i, 7) = 0 THEN 'CANCELLED'
            ELSE 'COMPLETED'
        END;

        INSERT IGNORE INTO orders (
            id, order_no, user_id, shop_id, status, total_amount, paid_at, created_at
        ) VALUES (
            i,
            CONCAT('DP2026', LPAD(i, 6, '0')),
            MOD(i - 1, 20) + 1,
            MOD(i - 1, 5) + 1,
            order_status,
            order_total,
            CASE WHEN order_status = 'CANCELLED' THEN NULL ELSE DATE_ADD(order_time, INTERVAL 1 HOUR) END,
            order_time
        );

        INSERT IGNORE INTO order_items (id, order_id, product_id, quantity, unit_price, amount)
        VALUES
            (i * 2 - 1, i, first_product_id, first_quantity, first_price, first_price * first_quantity),
            (i * 2, i, second_product_id, second_quantity, second_price, second_price * second_quantity);

        SET i = i + 1;
    END WHILE;
END$$

DELIMITER ;

CALL seed_ecommerce_demo();
DROP PROCEDURE seed_ecommerce_demo;
