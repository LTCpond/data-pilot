DROP PROCEDURE IF EXISTS create_data_pilot_distractor_tables;
DELIMITER $$
CREATE PROCEDURE create_data_pilot_distractor_tables()
BEGIN
    DECLARE table_names JSON DEFAULT JSON_ARRAY(
        'shipment_records','shipment_routes','carriers','delivery_events','warehouses',
        'warehouse_zones','inventory_snapshots','inventory_adjustments','stock_transfers','purchase_orders',
        'purchase_order_items','suppliers','supplier_contracts','campaigns','campaign_channels',
        'ad_impressions','ad_clicks','coupons','coupon_redemptions','loyalty_accounts',
        'loyalty_points','invoices','invoice_lines','payments','payment_refunds',
        'settlement_batches','expense_claims','budgets','tax_records','customer_tickets',
        'ticket_messages','ticket_categories','service_agents','satisfaction_surveys','chat_sessions',
        'wishlists','wishlist_items','product_reviews','review_replies','product_tags',
        'tag_relations','price_history','promotion_rules','notification_logs','audit_events'
    );
    DECLARE index_no INT DEFAULT 0;
    DECLARE table_name VARCHAR(64);
    WHILE index_no < JSON_LENGTH(table_names) DO
        SET table_name = JSON_UNQUOTE(JSON_EXTRACT(table_names, CONCAT('$[', index_no, ']')));
        SET @create_sql = CONCAT(
            'CREATE TABLE IF NOT EXISTS `', table_name, '` (',
            'id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT ''记录ID'',',
            'name VARCHAR(128) NULL COMMENT ''名称'',',
            'status VARCHAR(32) NULL COMMENT ''状态'',',
            'amount DECIMAL(14,2) NULL COMMENT ''金额'',',
            'created_at DATETIME NOT NULL COMMENT ''创建时间'',',
            'PRIMARY KEY (id), KEY idx_', table_name, '_created (created_at)',
            ') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT=''',
            REPLACE(table_name, '_', ' '), '干扰演示表'''
        );
        PREPARE statement FROM @create_sql;
        EXECUTE statement;
        DEALLOCATE PREPARE statement;
        SET index_no = index_no + 1;
    END WHILE;
END$$
DELIMITER ;
CALL create_data_pilot_distractor_tables();
DROP PROCEDURE create_data_pilot_distractor_tables;
