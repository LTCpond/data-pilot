package com.ltcpond.datapilot.core.query;

import com.ltcpond.datapilot.ai.rag.RagProperties;
import com.ltcpond.datapilot.ai.rag.SchemaVectorIndex;
import com.ltcpond.datapilot.ai.rag.SchemaVectorMatch;
import com.ltcpond.datapilot.core.datasource.DatasourceSchemaView;
import com.ltcpond.datapilot.core.datasource.SchemaRelationView;
import com.ltcpond.datapilot.core.datasource.SchemaTableView;
import com.ltcpond.datapilot.datasource.entity.DatasourceEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** 执行一次向量召回，并补充问题直达表与一跳双向外键表。 */
@Component
@RequiredArgsConstructor
public class SchemaRetriever {

    private final SchemaVectorIndex vectorIndex;
    private final RagProperties properties;

    public SchemaRetrievalResult retrieve(
            DatasourceEntity datasource, DatasourceSchemaView fullSchema, String question) {
        Instant startedAt = Instant.now();
        int total = fullSchema.tables().size();
        if (!properties.isEnabled()
                || properties.getMode() == RagProperties.Mode.FULL_SCHEMA
                || total <= properties.getFullSchemaThreshold()) {
            return full(fullSchema, false, startedAt);
        }
        if (!vectorIndex.available()
                || datasource.getRagIndexVersion() == null
                || datasource.getRagIndexVersion().isBlank()) {
            return full(fullSchema, true, startedAt);
        }

        try {
            LinkedHashSet<String> selected = new LinkedHashSet<>();
            List<SchemaVectorMatch> matches = vectorIndex.search(
                    datasource.getId(), datasource.getRagIndexVersion(), question, properties.getTopK());
            matches.forEach(match -> selected.add(normalize(match.tableName())));
            addDirectMentions(selected, fullSchema, question);
            addOneHopRelations(selected, fullSchema);

            List<SchemaTableView> promptTables = fullSchema.tables().stream()
                    .filter(table -> selected.contains(normalize(table.name())))
                    .limit(properties.getMaxPromptTables())
                    .toList();
            if (promptTables.isEmpty()) {
                return full(fullSchema, true, startedAt);
            }
            List<String> names = promptTables.stream().map(SchemaTableView::name).toList();
            RetrievalView view = new RetrievalView(
                    "RAG", false, total, promptTables.size(), names, elapsed(startedAt));
            return new SchemaRetrievalResult(
                    new DatasourceSchemaView(fullSchema.datasourceId(), promptTables), view);
        } catch (RuntimeException exception) {
            return full(fullSchema, true, startedAt);
        }
    }

    private void addDirectMentions(
            Set<String> selected, DatasourceSchemaView schema, String question) {
        String normalizedQuestion = question.toLowerCase(Locale.ROOT);
        for (SchemaTableView table : schema.tables()) {
            if (normalizedQuestion.contains(table.name().toLowerCase(Locale.ROOT))) {
                selected.add(normalize(table.name()));
            }
        }
    }

    private void addOneHopRelations(Set<String> selected, DatasourceSchemaView schema) {
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        for (SchemaTableView table : schema.tables()) {
            graph.computeIfAbsent(normalize(table.name()), ignored -> new LinkedHashSet<>());
        }
        for (SchemaTableView table : schema.tables()) {
            for (SchemaRelationView relation : table.foreignKeys()) {
                String source = normalize(unqualified(relation.sourceTable()));
                String target = normalize(unqualified(relation.targetTable()));
                graph.computeIfAbsent(source, ignored -> new LinkedHashSet<>()).add(target);
                graph.computeIfAbsent(target, ignored -> new LinkedHashSet<>()).add(source);
            }
        }
        List<String> base = new ArrayList<>(selected);
        for (String table : base) {
            for (String neighbor : graph.getOrDefault(table, Set.of())) {
                if (selected.size() >= properties.getMaxPromptTables()) {
                    return;
                }
                selected.add(neighbor);
            }
        }
    }

    private SchemaRetrievalResult full(
            DatasourceSchemaView schema, boolean fallback, Instant startedAt) {
        List<String> names = schema.tables().stream().map(SchemaTableView::name).toList();
        RetrievalView view = new RetrievalView(
                "FULL_SCHEMA", fallback, names.size(), names.size(), names, elapsed(startedAt));
        return new SchemaRetrievalResult(schema, view);
    }

    private String unqualified(String name) {
        int separator = name.lastIndexOf('.');
        return separator < 0 ? name : name.substring(separator + 1);
    }

    private String normalize(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private long elapsed(Instant startedAt) {
        return Duration.between(startedAt, Instant.now()).toMillis();
    }
}
