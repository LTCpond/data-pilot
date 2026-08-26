package com.ltcpond.datapilot.evaluation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationResultComparatorTest {

    private final EvaluationResultComparator comparator = new EvaluationResultComparator();

    @Test
    void shouldIgnoreAliasesColumnOrderAndNumericTrailingZeros() {
        Map<String, Object> generated = new LinkedHashMap<>();
        generated.put("sales", new BigDecimal("12.00"));
        generated.put("shop", "杭州示范店");
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("store_name", "杭州示范店");
        expected.put("amount", 12L);

        assertThat(comparator.compare("UNORDERED_ROWS", List.of(generated), List.of(expected))).isTrue();
    }

    @Test
    void shouldRespectOrderedAndUnorderedRows() {
        List<Map<String, Object>> generated = List.of(Map.of("value", 2), Map.of("value", 1));
        List<Map<String, Object>> expected = List.of(Map.of("value", 1), Map.of("value", 2));

        assertThat(comparator.compare("ORDERED_ROWS", generated, expected)).isFalse();
        assertThat(comparator.compare("UNORDERED_ROWS", generated, expected)).isTrue();
    }

    @Test
    void shouldHandleScalarAndEmptyResults() {
        assertThat(comparator.compare("SCALAR", List.of(Map.of("a", new BigDecimal("60.0"))),
                List.of(Map.of("b", 60)))).isTrue();
        assertThat(comparator.compare("EMPTY", List.of(), List.of())).isTrue();
    }
}
