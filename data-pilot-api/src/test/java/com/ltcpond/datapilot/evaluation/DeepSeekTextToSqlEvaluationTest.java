package com.ltcpond.datapilot.evaluation;

import com.ltcpond.datapilot.DataPilotApplication;
import com.ltcpond.datapilot.core.query.QueryCommand;
import com.ltcpond.datapilot.common.api.ResponseCode;
import com.ltcpond.datapilot.common.exception.AppException;
import com.ltcpond.datapilot.core.query.QueryResultView;
import com.ltcpond.datapilot.core.query.QueryTaskView;
import com.ltcpond.datapilot.core.query.AgentStepView;
import com.ltcpond.datapilot.core.query.QueryService;
import com.ltcpond.datapilot.datasource.connection.DatasourceConnectionInfo;
import com.ltcpond.datapilot.datasource.crypto.CredentialCipher;
import com.ltcpond.datapilot.datasource.entity.DatasourceEntity;
import com.ltcpond.datapilot.datasource.entity.QueryTaskEntity;
import com.ltcpond.datapilot.datasource.query.QueryExecutionResult;
import com.ltcpond.datapilot.datasource.query.ReadOnlyQueryExecutor;
import com.ltcpond.datapilot.datasource.store.DatasourceStore;
import com.ltcpond.datapilot.datasource.store.QueryTaskStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实模型评测。默认构建会跳过；只有专用脚本设置开关后才访问 DeepSeek。
 */
@Tag("evaluation")
@EnabledIfEnvironmentVariable(named = "DATA_PILOT_RUN_AI_EVALUATION", matches = "true")
@SpringBootTest(classes = DataPilotApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DeepSeekTextToSqlEvaluationTest {

    private static final String DEFAULT_DATASOURCE_NAME = "电商演示库";
    private static final int TOTAL_CASES = 20;
    private final EvaluationResultComparator resultComparator = new EvaluationResultComparator();

    @Autowired
    private QueryService queryService;
    @Autowired
    private DatasourceStore datasourceStore;
    @Autowired
    private QueryTaskStore taskStore;
    @Autowired
    private CredentialCipher credentialCipher;
    @Autowired
    private ReadOnlyQueryExecutor queryExecutor;

    @Test
    void evaluateTwentyFixedQuestions() throws IOException {
        List<EvaluationCase> cases = loadCases();
        assertThat(cases).hasSize(TOTAL_CASES);
        String datasourceName = System.getProperty(
                "data-pilot.evaluation.datasource-name", DEFAULT_DATASOURCE_NAME);
        DatasourceEntity datasource = datasourceStore.findByName(datasourceName)
                .filter(item -> "READY".equals(item.getStatus()))
                .orElseThrow(() -> new IllegalStateException(
                        "Demo datasource is not READY; run register-demo-datasource.ps1 first"));
        DatasourceConnectionInfo connection = new DatasourceConnectionInfo(
                datasource.getJdbcUrl(), datasource.getUsername(),
                credentialCipher.decrypt(datasource.getEncryptedPassword()));

        List<CaseResult> results = new ArrayList<>();
        for (EvaluationCase evaluationCase : cases) {
            results.add(evaluateCase(evaluationCase, datasource.getId(), connection));
        }

        EvaluationSummary summary = summarize(results);
        writeReports(results, summary);

        assertThat(summary.rejectedPassed()).as("three unsafe or unanswerable questions").isEqualTo(3);
        assertThat(summary.answerablePassed()).as("answerable semantic result matches").isGreaterThanOrEqualTo(15);
        assertThat(summary.passRate()).as("overall pass rate").isGreaterThanOrEqualTo(0.90);
    }

    private CaseResult evaluateCase(
            EvaluationCase evaluationCase,
            long datasourceId,
            DatasourceConnectionInfo connection) {
        long startedAt = System.nanoTime();
        boolean passed = false;
        String detail = "result mismatch";
        Long taskId = null;
        com.ltcpond.datapilot.core.query.RetrievalView retrieval = null;
        try {
            QueryTaskView task = queryService.createTask(
                    new QueryCommand(datasourceId, evaluationCase.question(), 200));
            QueryResultView generated = queryService.executeTask(task.id());
            taskId = task.id();
            if (generated == null) {
                QueryTaskView terminal = queryService.get(task.id());
                passed = evaluationCase.comparison() == Comparison.REJECTED
                        && "NEEDS_CLARIFICATION".equals(terminal.status());
                detail = passed ? "clarification requested" : "unexpected empty result";
            } else {
                retrieval = generated.retrieval();
                if (evaluationCase.comparison() == Comparison.REJECTED) {
                    detail = "expected rejection but SQL executed";
                } else {
                    QueryExecutionResult expected = queryExecutor.execute(
                            connection, evaluationCase.referenceSql(), 200, task.id());
                    passed = resultComparator.compare(
                            evaluationCase.comparison().name(), generated.rows(), expected.rows());
                    detail = passed ? "matched" : "result mismatch";
                }
            }
        } catch (AppException exception) {
            taskId = newestTaskId(datasourceId, evaluationCase.question());
            if (exception.getResponseCode() == ResponseCode.QUERY_REJECTED
                    || exception.getResponseCode() == ResponseCode.READ_ONLY_QUERY_EXECUTION_FAILED) {
                passed = evaluationCase.comparison() == Comparison.REJECTED;
                detail = passed ? "safely rejected" : "unexpected rejection";
            } else {
                detail = "workflow error: " + exception.getResponseCode().name();
            }
        } catch (RuntimeException exception) {
            taskId = newestTaskId(datasourceId, evaluationCase.question());
            detail = "workflow error: " + exception.getClass().getSimpleName();
        }

        QueryTaskEntity task = taskId == null ? null : taskStore.findTask(taskId).orElse(null);
        if (retrieval == null && task != null && task.getSchemaTableCount() != null) {
            retrieval = new com.ltcpond.datapilot.core.query.RetrievalView(
                    Boolean.TRUE.equals(task.getRagUsed()) ? "RAG" : "FULL_SCHEMA",
                    Boolean.TRUE.equals(task.getRagFallback()),
                    task.getSchemaTableCount(),
                    task.getPromptTableCount() == null ? task.getSchemaTableCount() : task.getPromptTableCount(),
                    splitTables(task.getRetrievedTables()),
                    task.getRetrievalDurationMs() == null ? 0L : task.getRetrievalDurationMs());
        }
        List<AgentStepView> agentSteps = taskId == null ? List.of() : queryService.steps(taskId);
        int promptTokens = agentSteps.stream().map(AgentStepView::promptTokens)
                .filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).sum();
        int completionTokens = agentSteps.stream().map(AgentStepView::completionTokens)
                .filter(java.util.Objects::nonNull).mapToInt(Integer::intValue).sum();
        int totalTokens = promptTokens + completionTokens;
        int repairs = task == null || task.getRepairCount() == null ? 0 : task.getRepairCount();
        long durationMs = task != null && task.getDurationMs() != null
                ? task.getDurationMs()
                : (System.nanoTime() - startedAt) / 1_000_000;
        boolean requiredRecalled = evaluationCase.requiredTables().isEmpty()
                || retrieval != null && retrieval.retrievedTables().containsAll(evaluationCase.requiredTables());
        return new CaseResult(
                evaluationCase.id(), evaluationCase.question(), evaluationCase.comparison(), passed,
                detail, taskId, repairs, durationMs, promptTokens, completionTokens, totalTokens,
                requiredRecalled,
                retrieval == null ? 0 : retrieval.promptTableCount(),
                retrieval == null ? 0 : retrieval.durationMs(),
                retrieval != null && retrieval.fallback());
    }

    private Long newestTaskId(long datasourceId, String question) {
        return taskStore.findTasks(datasourceId).stream()
                .filter(task -> question.equals(task.getQuestion()))
                .map(QueryTaskEntity::getId)
                .findFirst()
                .orElse(null);
    }


    private EvaluationSummary summarize(List<CaseResult> results) {
        int passed = (int) results.stream().filter(CaseResult::passed).count();
        int answerablePassed = (int) results.stream()
                .filter(result -> result.comparison() != Comparison.REJECTED && result.passed()).count();
        int rejectedPassed = (int) results.stream()
                .filter(result -> result.comparison() == Comparison.REJECTED && result.passed()).count();
        int firstPass = (int) results.stream()
                .filter(result -> result.passed() && result.repairs() == 0).count();
        int repairedPass = (int) results.stream()
                .filter(result -> result.passed() && result.repairs() > 0).count();
        List<Long> durations = results.stream().map(CaseResult::durationMs).sorted().toList();
        double averageLatency = durations.stream().mapToLong(Long::longValue).average().orElse(0);
        int p95Index = Math.max(0, (int) Math.ceil(durations.size() * 0.95) - 1);
        long p95Latency = durations.isEmpty() ? 0 : durations.get(p95Index);
        return new EvaluationSummary(
                passed, answerablePassed, rejectedPassed,
                (double) passed / results.size(), (double) firstPass / results.size(), repairedPass,
                averageLatency, p95Latency,
                results.stream().mapToInt(CaseResult::promptTokens).sum(),
                results.stream().mapToInt(CaseResult::completionTokens).sum(),
                results.stream().mapToInt(CaseResult::totalTokens).sum(),
                (int) results.stream()
                        .filter(result -> result.comparison() != Comparison.REJECTED)
                        .filter(CaseResult::requiredRecalled).count(),
                results.stream().mapToInt(CaseResult::promptTableCount).average().orElse(0),
                results.stream().mapToLong(CaseResult::retrievalDurationMs).average().orElse(0),
                (int) results.stream().filter(CaseResult::fallback).count());
    }

    private List<EvaluationCase> loadCases() throws IOException {
        InputStream stream = getClass().getResourceAsStream("/evaluation/text-to-sql-cases.tsv");
        if (stream == null) {
            throw new IllegalStateException("缺少 Text-to-SQL 评测数据集");
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .map(line -> line.split("\t", -1))
                    .map(parts -> new EvaluationCase(
                            Integer.parseInt(parts[0]), Comparison.valueOf(parts[1]), parts[2], parts[3],
                            parts.length < 5 ? List.of() : splitTables(parts[4])))
                    .toList();
        }
    }

    private void writeReports(List<CaseResult> results, EvaluationSummary summary) throws IOException {
        String round = safeRoundName(System.getProperty("data-pilot.evaluation.round", "baseline"));
        Path directory = Path.of("target", "text-to-sql-evaluation");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("round-" + round + ".json"), jsonReport(results, summary),
                StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("round-" + round + ".md"), markdownReport(results, summary),
                StandardCharsets.UTF_8);
    }

    private String jsonReport(List<CaseResult> results, EvaluationSummary summary) {
        StringBuilder json = new StringBuilder();
        json.append("{\n  \"promptVersion\": \"data-agent-v1\",")
                .append("\n  \"model\": \"deepseek-v4-pro\",")
                .append("\n  \"passed\": ").append(summary.passed()).append(',')
                .append("\n  \"passRate\": ").append(decimal(summary.passRate())).append(',')
                .append("\n  \"firstPassRate\": ").append(decimal(summary.firstPassRate())).append(',')
                .append("\n  \"repairedPassCount\": ").append(summary.repairedPassCount()).append(',')
                .append("\n  \"answerablePassed\": ").append(summary.answerablePassed()).append(',')
                .append("\n  \"rejectedPassed\": ").append(summary.rejectedPassed()).append(',')
                .append("\n  \"averageLatencyMs\": ").append(decimal(summary.averageLatencyMs())).append(',')
                .append("\n  \"p95LatencyMs\": ").append(summary.p95LatencyMs()).append(',')
                .append("\n  \"promptTokens\": ").append(summary.promptTokens()).append(',')
                .append("\n  \"completionTokens\": ").append(summary.completionTokens()).append(',')
                .append("\n  \"totalTokens\": ").append(summary.totalTokens()).append(',')
                .append("\n  \"requiredRecallCount\": ").append(summary.requiredRecallCount()).append(',')
                .append("\n  \"averagePromptTableCount\": ").append(decimal(summary.averagePromptTableCount())).append(',')
                .append("\n  \"averageRetrievalDurationMs\": ").append(decimal(summary.averageRetrievalDurationMs())).append(',')
                .append("\n  \"fallbackCount\": ").append(summary.fallbackCount()).append(',')
                .append("\n  \"cases\": [\n");
        for (int index = 0; index < results.size(); index++) {
            CaseResult result = results.get(index);
            json.append("    {\"id\":").append(result.id())
                    .append(",\"passed\":").append(result.passed())
                    .append(",\"repairs\":").append(result.repairs())
                    .append(",\"durationMs\":").append(result.durationMs())
                    .append(",\"totalTokens\":").append(result.totalTokens())
                    .append(",\"requiredRecalled\":").append(result.requiredRecalled())
                    .append(",\"promptTableCount\":").append(result.promptTableCount())
                    .append(",\"retrievalDurationMs\":").append(result.retrievalDurationMs())
                    .append(",\"fallback\":").append(result.fallback())
                    .append(",\"detail\":\"").append(escapeJson(result.detail())).append("\"}");
            json.append(index + 1 == results.size() ? '\n' : ",\n");
        }
        return json.append("  ]\n}\n").toString();
    }

    private String markdownReport(List<CaseResult> results, EvaluationSummary summary) {
        StringBuilder markdown = new StringBuilder("# Data Pilot Text-to-SQL Evaluation\n\n");
        markdown.append("- Prompt: `data-agent-v1`\n")
                .append("- 通过率: ").append(decimal(summary.passRate() * 100)).append("%\n")
                .append("- 首次成功率: ").append(decimal(summary.firstPassRate() * 100)).append("%\n")
                .append("- 纠错后通过数: ").append(summary.repairedPassCount()).append("\n")
                .append("- 可回答题: ").append(summary.answerablePassed()).append("/17\n")
                .append("- 安全拒绝题: ").append(summary.rejectedPassed()).append("/3\n")
                .append("- 平均/P95 延迟: ").append(decimal(summary.averageLatencyMs()))
                .append("/").append(summary.p95LatencyMs()).append(" ms\n")
                .append("- Token: prompt=").append(summary.promptTokens())
                .append(", completion=").append(summary.completionTokens())
                .append(", total=").append(summary.totalTokens()).append("\n\n")
                .append("- 必需表召回题数: ").append(summary.requiredRecallCount()).append("/17\n")
                .append("- 平均 Prompt 表数: ").append(decimal(summary.averagePromptTableCount())).append("\n")
                .append("- 平均向量检索延迟: ").append(decimal(summary.averageRetrievalDurationMs())).append(" ms\n")
                .append("- 回退次数: ").append(summary.fallbackCount()).append("\n\n")
                .append("| ID | 结果 | 纠错 | 耗时(ms) | Token | 说明 |\n")
                .append("|---:|:---:|---:|---:|---:|---|\n");
        for (CaseResult result : results) {
            markdown.append('|').append(result.id()).append('|')
                    .append(result.passed() ? "PASS" : "FAIL").append('|')
                    .append(result.repairs()).append('|').append(result.durationMs()).append('|')
                    .append(result.totalTokens()).append('|').append(result.detail()).append("|\n");
        }
        return markdown.toString();
    }

    private String safeRoundName(String value) {
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.matches("baseline|final|full-schema-50|rag-50") ? normalized : "baseline";
    }

    private static List<String> splitTables(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private String decimal(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", "\\r").replace("\n", "\\n");
    }

    private enum Comparison {
        SCALAR, ORDERED_ROWS, UNORDERED_ROWS, EMPTY, REJECTED
    }

    private record EvaluationCase(
            int id, Comparison comparison, String question, String referenceSql, List<String> requiredTables) {
    }

    private record CaseResult(
            int id,
            String question,
            Comparison comparison,
            boolean passed,
            String detail,
            Long taskId,
            int repairs,
            long durationMs,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            boolean requiredRecalled,
            int promptTableCount,
            long retrievalDurationMs,
            boolean fallback) {
    }

    private record EvaluationSummary(
            int passed,
            int answerablePassed,
            int rejectedPassed,
            double passRate,
            double firstPassRate,
            int repairedPassCount,
            double averageLatencyMs,
            long p95LatencyMs,
            int promptTokens,
            int completionTokens,
            int totalTokens,
            int requiredRecallCount,
            double averagePromptTableCount,
            double averageRetrievalDurationMs,
            int fallbackCount) {
    }
}
