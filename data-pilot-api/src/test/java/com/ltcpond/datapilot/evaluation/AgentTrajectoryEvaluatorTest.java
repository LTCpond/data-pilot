package com.ltcpond.datapilot.evaluation;

import com.ltcpond.datapilot.core.query.AgentStepView;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentTrajectoryEvaluatorTest {

    @Test
    void shouldScoreToolSelectionOrderAndEfficiency() {
        AgentTrajectoryEvaluator.Metrics metrics = new AgentTrajectoryEvaluator().evaluate(
                "SUCCEEDED",
                List.of("search_schema", "get_schema", "execute_readonly_sql"),
                "SUCCEEDED",
                List.of(
                        step(1, "search_schema"),
                        step(2, "get_schema"),
                        step(3, "execute_readonly_sql")));

        assertThat(metrics.outcomeMatched()).isTrue();
        assertThat(metrics.toolPrecision()).isEqualTo(1);
        assertThat(metrics.toolRecall()).isEqualTo(1);
        assertThat(metrics.toolF1()).isEqualTo(1);
        assertThat(metrics.toolOrderScore()).isEqualTo(1);
        assertThat(metrics.stepEfficiency()).isEqualTo(1);
    }

    @Test
    void shouldPenalizeExtraAndOutOfOrderTools() {
        AgentTrajectoryEvaluator.Metrics metrics = new AgentTrajectoryEvaluator().evaluate(
                "NEEDS_CLARIFICATION",
                List.of("search_schema", "execute_readonly_sql"),
                "FAILED",
                List.of(
                        step(1, "execute_readonly_sql"),
                        step(2, "search_schema"),
                        step(3, "execute_readonly_sql")));

        assertThat(metrics.outcomeMatched()).isFalse();
        assertThat(metrics.toolPrecision()).isLessThan(1);
        assertThat(metrics.toolOrderScore()).isLessThan(1);
        assertThat(metrics.stepEfficiency()).isLessThan(1);
    }

    private AgentStepView step(int number, String tool) {
        LocalDateTime now = LocalDateTime.now();
        return new AgentStepView(
                number, 1L, number, "TOOL", tool, "SUCCEEDED", "安全摘要",
                null, 1L, null, null, now, now);
    }
}
