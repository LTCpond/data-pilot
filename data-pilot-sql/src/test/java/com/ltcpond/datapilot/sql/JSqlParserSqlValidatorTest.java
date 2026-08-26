package com.ltcpond.datapilot.sql;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JSqlParserSqlValidatorTest {

    private final JSqlParserSqlValidator validator = new JSqlParserSqlValidator();
    private final Set<String> allowedTables = Set.of(
            "orders", "products", "order_items",
            "ecommerce_demo.orders", "ecommerce_demo.products", "ecommerce_demo.order_items");

    @Test
    void shouldAcceptSelectAndAppendLimit() {
        SqlValidationResult result = validate("SELECT id, total_amount FROM orders");

        assertThat(result.valid())
                .withFailMessage("unexpected violations: %s", result.violations())
                .isTrue();
        assertThat(result.executableSql()).endsWith("LIMIT 100");
    }

    @Test
    void shouldReduceAnExcessiveLimit() {
        SqlValidationResult result = validate("SELECT * FROM orders LIMIT 500 OFFSET 10");

        assertThat(result.valid())
                .withFailMessage("unexpected violations: %s", result.violations())
                .isTrue();
        assertThat(result.executableSql()).endsWith("LIMIT 100 OFFSET 10");
    }

    @Test
    void shouldSafelyReduceLimitLargerThanLong() {
        SqlValidationResult result = validate(
                "SELECT * FROM orders LIMIT 999999999999999999999999999999999999");

        assertThat(result.valid()).isTrue();
        assertThat(result.executableSql()).endsWith("LIMIT 100");
    }

    @Test
    void shouldRejectWriteAndMultipleStatements() {
        assertThat(validate("DELETE FROM orders").violations()).contains("NON_SELECT_STATEMENT");
        assertThat(validate("SELECT * FROM orders; DELETE FROM orders").violations())
                .contains("MULTIPLE_STATEMENTS");
    }

    @Test
    void shouldRejectUnknownAndSystemTables() {
        assertThat(validate("SELECT * FROM secret_table").violations()).contains("UNAUTHORIZED_TABLE");
        assertThat(validate("SELECT * FROM other_database.orders").violations()).contains("UNAUTHORIZED_TABLE");
        assertThat(validate("SELECT * FROM mysql.user").violations()).contains("SYSTEM_SCHEMA_ACCESS");
    }

    @Test
    void shouldRejectDangerousMysqlFeatures() {
        assertThat(validate("SELECT SLEEP(10) FROM orders").violations())
                .contains("DANGEROUS_SQL_FEATURE");
        assertThat(validate("SELECT * FROM orders FOR UPDATE").violations())
                .contains("DANGEROUS_SQL_FEATURE");
    }

    @Test
    void shouldRejectUnknownQualifiedColumn() {
        assertThat(validate("SELECT o.missing_column FROM orders o").violations())
                .contains("UNKNOWN_COLUMN");
    }

    @Test
    void shouldAllowCteAndOutputAlias() {
        SqlValidationResult result = validate("""
                WITH recent AS (SELECT o.id FROM orders o)
                SELECT r.id AS order_id FROM recent r ORDER BY order_id
                """);

        assertThat(result.valid())
                .withFailMessage("unexpected violations: %s", result.violations())
                .isTrue();
    }

    private SqlValidationResult validate(String sql) {
        Map<String, Set<String>> columns = Map.of(
                "orders", Set.of("id", "total_amount"),
                "ecommerce_demo.orders", Set.of("id", "total_amount"),
                "products", Set.of("id", "name"),
                "ecommerce_demo.products", Set.of("id", "name"),
                "order_items", Set.of("id", "order_id", "product_id"),
                "ecommerce_demo.order_items", Set.of("id", "order_id", "product_id"));
        return validator.validate(new SqlValidationRequest(sql, allowedTables, columns, 100));
    }
}
