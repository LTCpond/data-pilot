package com.ltcpond.datapilot.evaluation;

import java.math.BigDecimal;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Map;

/** 评测结果规范化器：忽略列别名和数值尾零，并按用例决定是否保留行顺序。 */
final class EvaluationResultComparator {

    boolean compare(
            String comparison,
            List<Map<String, Object>> generated,
            List<Map<String, Object>> expected) {
        if ("EMPTY".equals(comparison)) {
            return generated.isEmpty() && expected.isEmpty();
        }
        if ("SCALAR".equals(comparison)) {
            return generated.size() == 1 && expected.size() == 1
                    && canonicalValues(generated.getFirst()).equals(canonicalValues(expected.getFirst()));
        }
        List<String> generatedRows = generated.stream().map(this::canonicalRow).toList();
        List<String> expectedRows = expected.stream().map(this::canonicalRow).toList();
        if ("UNORDERED_ROWS".equals(comparison)) {
            generatedRows = generatedRows.stream().sorted().toList();
            expectedRows = expectedRows.stream().sorted().toList();
        }
        return generatedRows.equals(expectedRows);
    }

    private String canonicalRow(Map<String, Object> row) {
        return String.join("\u001f", canonicalValues(row));
    }

    private List<String> canonicalValues(Map<String, Object> row) {
        return row.values().stream().map(this::canonicalValue).sorted().toList();
    }

    private String canonicalValue(Object value) {
        if (value == null) {
            return "<NULL>";
        }
        if (value instanceof Number number) {
            try {
                return new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
            } catch (NumberFormatException ignored) {
                return number.toString();
            }
        }
        if (value instanceof TemporalAccessor) {
            return value.toString();
        }
        return value.toString().strip();
    }
}
