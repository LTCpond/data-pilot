package com.ltcpond.datapilot.evaluation;

import com.ltcpond.datapilot.DataPilotApplication;
import com.ltcpond.datapilot.ai.rag.SchemaVectorIndex;
import com.ltcpond.datapilot.core.datasource.DatasourceSchemaView;
import com.ltcpond.datapilot.core.datasource.DatasourceService;
import com.ltcpond.datapilot.core.query.SchemaRetrievalResult;
import com.ltcpond.datapilot.core.query.SchemaRetriever;
import com.ltcpond.datapilot.datasource.entity.DatasourceEntity;
import com.ltcpond.datapilot.datasource.store.DatasourceStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/** 只调用 Ollama/pgvector 的召回校准，不调用 DeepSeek。 */
@Tag("evaluation")
@EnabledIfEnvironmentVariable(named = "DATA_PILOT_RUN_RAG_RETRIEVAL_EVALUATION", matches = "true")
@SpringBootTest(classes = DataPilotApplication.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SchemaRetrievalEvaluationTest {

    @Autowired
    private DatasourceStore datasourceStore;
    @Autowired
    private DatasourceService datasourceService;
    @Autowired
    private SchemaRetriever schemaRetriever;
    @Autowired
    private SchemaVectorIndex schemaVectorIndex;

    @Test
    void requiredTableRecallMustReachNinetyFivePercent() throws Exception {
        DatasourceEntity datasource = datasourceStore.findByName("电商RAG演示库")
                .filter(item -> "READY".equals(item.getStatus()))
                .orElseThrow(() -> new IllegalStateException("RAG demo datasource is not READY"));
        DatasourceSchemaView schema = datasourceService.schema(datasource.getId());
        assertThat(schema.tables()).hasSize(50);
        assertThat(schemaVectorIndex.search(
                datasource.getId(), datasource.getRagIndexVersion(), "查询订单数量", 1))
                .as("active pgvector index must be searchable before recall evaluation")
                .isNotEmpty();

        List<RetrievalCase> cases = loadCases();
        int requiredCount = 0;
        int recalledCount = 0;
        int promptTableCount = 0;
        int fallbackCount = 0;
        List<String> lines = new ArrayList<>();
        for (RetrievalCase item : cases) {
            SchemaRetrievalResult result = schemaRetriever.retrieve(datasource, schema, item.question());
            List<String> recalled = result.view().retrievedTables().stream()
                    .map(value -> value.toLowerCase(Locale.ROOT)).toList();
            int hits = (int) item.requiredTables().stream()
                    .map(value -> value.toLowerCase(Locale.ROOT)).filter(recalled::contains).count();
            requiredCount += item.requiredTables().size();
            recalledCount += hits;
            promptTableCount += result.view().promptTableCount();
            fallbackCount += result.view().fallback() ? 1 : 0;
            lines.add(item.id() + "\t" + hits + "/" + item.requiredTables().size()
                    + "\t" + result.view().promptTableCount() + "\t"
                    + String.join(",", result.view().retrievedTables()));
        }
        double recall = requiredCount == 0 ? 1D : (double) recalledCount / requiredCount;
        double averagePromptTables = (double) promptTableCount / cases.size();
        Path directory = Path.of("target", "text-to-sql-evaluation");
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("rag-retrieval.tsv"),
                "id\trecall\tpromptTables\tretrievedTables\n" + String.join("\n", lines) + "\n",
                StandardCharsets.UTF_8);

        assertThat(recall).isGreaterThanOrEqualTo(0.95);
        assertThat(averagePromptTables).isLessThanOrEqualTo(12D);
        assertThat(fallbackCount).isZero();
    }

    private List<RetrievalCase> loadCases() throws Exception {
        InputStream stream = getClass().getResourceAsStream("/evaluation/text-to-sql-cases.tsv");
        if (stream == null) {
            throw new IllegalStateException("Evaluation dataset is missing");
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .map(line -> line.split("\t", -1))
                    .filter(parts -> parts.length >= 5 && !parts[4].isBlank())
                    .map(parts -> new RetrievalCase(
                            Integer.parseInt(parts[0]), parts[2], Arrays.stream(parts[4].split(","))
                                    .map(String::trim).filter(value -> !value.isEmpty()).toList()))
                    .toList();
        }
    }

    private record RetrievalCase(int id, String question, List<String> requiredTables) {
    }
}
