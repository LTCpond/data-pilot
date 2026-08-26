package com.ltcpond.datapilot.core.rag;

import com.ltcpond.datapilot.ai.rag.RagProperties;
import com.ltcpond.datapilot.ai.rag.SchemaVectorIndex;
import com.ltcpond.datapilot.common.api.ResponseCode;
import com.ltcpond.datapilot.common.exception.AppException;
import com.ltcpond.datapilot.core.datasource.DatasourceSchemaView;
import com.ltcpond.datapilot.core.datasource.DatasourceService;
import com.ltcpond.datapilot.core.datasource.SchemaTableView;
import com.ltcpond.datapilot.datasource.entity.DatasourceEntity;
import com.ltcpond.datapilot.datasource.store.DatasourceStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchemaIndexServiceTest {

    private DatasourceStore store;
    private DatasourceService datasourceService;
    private SchemaVectorIndex vectorIndex;
    private SchemaIndexService service;

    @BeforeEach
    void setUp() {
        store = mock(DatasourceStore.class);
        datasourceService = mock(DatasourceService.class);
        vectorIndex = mock(SchemaVectorIndex.class);
        RagProperties properties = new RagProperties();
        properties.setEnabled(true);
        service = new SchemaIndexService(
                store, datasourceService, new SchemaDocumentBuilder(), vectorIndex, properties);

        DatasourceEntity datasource = new DatasourceEntity();
        datasource.setId(1L);
        datasource.setStatus("READY");
        datasource.setRagIndexVersion("old-version");
        when(store.findById(1L)).thenReturn(Optional.of(datasource));
        when(datasourceService.schema(1L)).thenReturn(new DatasourceSchemaView(1L, List.of(
                new SchemaTableView(1L, "demo", "orders", "TABLE", "订单表", List.of(), List.of()))));
    }

    @Test
    void shouldActivateNewVersionBeforeCleaningOldVersions() {
        RagIndexResultView result = service.rebuild(1L);

        assertThat(result.status()).isEqualTo("READY");
        assertThat(result.documentCount()).isEqualTo(1);
        verify(store).markRagReady(eq(1L), eq(result.indexVersion()), eq(1), any());
        verify(vectorIndex).deleteOtherVersions(1L, result.indexVersion());
    }

    @Test
    void shouldKeepOldVersionWhenNewIndexFails() {
        doThrow(new IllegalStateException("ollama unavailable")).when(vectorIndex).index(any());

        assertThatThrownBy(() -> service.rebuild(1L))
                .isInstanceOfSatisfying(AppException.class, exception ->
                        assertThat(exception.getResponseCode()).isEqualTo(
                                ResponseCode.SCHEMA_VECTOR_INDEX_FAILED));

        verify(store).markRagError(1L, "RAG_INDEX_FAILED");
        verify(store, never()).markRagReady(eq(1L), anyString(), anyInt(), any());
        verify(vectorIndex, never()).deleteOtherVersions(eq(1L), anyString());
    }
}
