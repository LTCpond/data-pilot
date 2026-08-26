package com.ltcpond.datapilot.core.rag;

import com.ltcpond.datapilot.ai.rag.RagProperties;
import com.ltcpond.datapilot.ai.rag.SchemaVectorDocument;
import com.ltcpond.datapilot.ai.rag.SchemaVectorIndex;
import com.ltcpond.datapilot.core.datasource.DatasourceNotFoundException;
import com.ltcpond.datapilot.core.datasource.DatasourceSchemaView;
import com.ltcpond.datapilot.core.datasource.DatasourceService;
import com.ltcpond.datapilot.datasource.entity.DatasourceEntity;
import com.ltcpond.datapilot.datasource.store.DatasourceStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 以新版本完整写入、成功切换、最后清旧版本的顺序维护 Schema 索引。 */
@Service
@RequiredArgsConstructor
public class SchemaIndexService {

    private final DatasourceStore store;
    private final DatasourceService datasourceService;
    private final SchemaDocumentBuilder documentBuilder;
    private final SchemaVectorIndex vectorIndex;
    private final RagProperties properties;

    public RagIndexResultView rebuild(long datasourceId) {
        DatasourceEntity datasource = store.findById(datasourceId)
                .orElseThrow(DatasourceNotFoundException::new);
        if (!properties.isEnabled()) {
            store.markRagError(datasourceId, "RAG_DISABLED");
            throw new RagIndexException();
        }
        DatasourceSchemaView schema = datasourceService.schema(datasourceId);
        if (schema.tables().isEmpty()) {
            store.markRagError(datasourceId, "SCHEMA_EMPTY");
            throw new RagIndexException();
        }

        String newVersion = UUID.randomUUID().toString();
        store.markRagIndexing(datasourceId);
        try {
            List<SchemaVectorDocument> documents = documentBuilder.build(datasourceId, newVersion, schema);
            vectorIndex.index(documents);
            LocalDateTime indexedAt = LocalDateTime.now();
            store.markRagReady(datasourceId, newVersion, documents.size(), indexedAt);
            try {
                vectorIndex.deleteOtherVersions(datasourceId, newVersion);
            } catch (RuntimeException ignored) {
                // 新版本已经激活，旧版本清理可在下次重建时重试。
            }
            return new RagIndexResultView(
                    datasourceId, "READY", documents.size(), newVersion, indexedAt);
        } catch (RuntimeException exception) {
            try {
                vectorIndex.deleteVersion(datasourceId, newVersion);
            } catch (RuntimeException ignored) {
                // 失败版本不可能成为活动版本，残留可由后续重建清理。
            }
            store.markRagError(datasourceId, "RAG_INDEX_FAILED");
            // datasource 的业务 READY 状态和旧 ragIndexVersion 都保持不变。
            if (datasource.getRagIndexVersion() != null) {
                // 旧活动版本只读可用，不做删除。
            }
            throw new RagIndexException();
        }
    }

    /** 元数据同步后的尽力索引，失败不能让同步接口失败。 */
    public RagIndexResultView rebuildAfterSync(long datasourceId) {
        return rebuild(datasourceId);
    }
}
