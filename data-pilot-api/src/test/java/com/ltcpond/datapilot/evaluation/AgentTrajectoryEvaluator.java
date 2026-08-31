package com.ltcpond.datapilot.evaluation;

import com.ltcpond.datapilot.core.query.AgentStepView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 计算 Agent 工具选择、顺序和步骤效率指标，供真实模型评测报告复用。 */
final class AgentTrajectoryEvaluator {

    Metrics evaluate(String expectedOutcome, List<String> expectedTools, String actualOutcome, List<AgentStepView> steps) {
        List<String> actualTools = steps.stream()
                .filter(step -> "TOOL".equals(step.kind()) && step.toolName() != null)
                .map(AgentStepView::toolName)
                .toList();
        int truePositive = multisetIntersection(expectedTools, actualTools);
        double precision = actualTools.isEmpty() ? (expectedTools.isEmpty() ? 1 : 0)
                : (double) truePositive / actualTools.size();
        double recall = expectedTools.isEmpty() ? 1 : (double) truePositive / expectedTools.size();
        double f1 = precision + recall == 0 ? 0 : 2 * precision * recall / (precision + recall);
        double orderScore = expectedTools.isEmpty() && actualTools.isEmpty() ? 1
                : (double) longestCommonSubsequence(expectedTools, actualTools)
                / Math.max(expectedTools.size(), actualTools.size());
        double stepEfficiency = actualTools.isEmpty()
                ? (expectedTools.isEmpty() ? 1 : 0)
                : Math.min(1, (double) Math.max(1, expectedTools.size()) / actualTools.size());
        return new Metrics(
                expectedOutcome.equals(actualOutcome), precision, recall, f1, orderScore,
                stepEfficiency, actualTools.size(),
                steps.stream().anyMatch(step -> "AGENT_MAX_TURNS".equals(step.errorKind())));
    }

    private int multisetIntersection(List<String> expected, List<String> actual) {
        Map<String, Integer> remaining = new HashMap<>();
        expected.forEach(tool -> remaining.merge(tool, 1, Integer::sum));
        int matches = 0;
        for (String tool : actual) {
            int count = remaining.getOrDefault(tool, 0);
            if (count > 0) {
                matches++;
                remaining.put(tool, count - 1);
            }
        }
        return matches;
    }

    private int longestCommonSubsequence(List<String> expected, List<String> actual) {
        int[][] lengths = new int[expected.size() + 1][actual.size() + 1];
        for (int left = 1; left <= expected.size(); left++) {
            for (int right = 1; right <= actual.size(); right++) {
                lengths[left][right] = expected.get(left - 1).equals(actual.get(right - 1))
                        ? lengths[left - 1][right - 1] + 1
                        : Math.max(lengths[left - 1][right], lengths[left][right - 1]);
            }
        }
        return lengths[expected.size()][actual.size()];
    }

    record Metrics(
            boolean outcomeMatched,
            double toolPrecision,
            double toolRecall,
            double toolF1,
            double toolOrderScore,
            double stepEfficiency,
            int toolCalls,
            boolean loopExhausted) {
    }
}
