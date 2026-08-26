package com.ltcpond.datapilot.core.query;

import com.ltcpond.datapilot.ai.rag.RagProperties;
import com.ltcpond.datapilot.ai.rag.SchemaVectorIndex;
import com.ltcpond.datapilot.ai.rag.SchemaVectorMatch;
import com.ltcpond.datapilot.core.datasource.DatasourceSchemaView;
import com.ltcpond.datapilot.core.datasource.SchemaRelationView;
import com.ltcpond.datapilot.core.datasource.SchemaTableView;
import com.ltcpond.datapilot.datasource.entity.DatasourceEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SchemaRetrieverTest {

    private SchemaVectorIndex vectorIndex;
    private RagProperties properties;
    private DatasourceEntity datasource;

    @BeforeEach
    void setUp() {
        vectorIndex = mock(SchemaVectorIndex.class);
        properties = new RagProperties();
        properties.setFullSchemaThreshold(10);
        properties.setTopK(6);
        properties.setMaxPromptTables(12);
        datasource = new DatasourceEntity();
        datasource.setId(1L);
        datasource.setRagIndexVersion("active-version");
    }

    @Test
    void shouldUseFullSchemaBelowThresholdWithoutFallback() {
        SchemaRetrievalResult result = retriever().retrieve(datasource, schema(5), "订单数量");

        assertThat(result.view().mode()).isEqualTo("FULL_SCHEMA");
        assertThat(result.view().fallback()).isFalse();
        assertThat(result.view().promptTableCount()).isEqualTo(5);
    }

    @Test
    void shouldAddDirectMentionAndBidirectionalForeignKeyNeighbor() {
        when(vectorIndex.available()).thenReturn(true);
        when(vectorIndex.search(anyLong(), anyString(), anyString(), anyInt()))
                .thenReturn(List.of(new SchemaVectorMatch("demo", "orders", 0.9)));

        SchemaRetrievalResult result = retriever().retrieve(
                datasource, schema(50), "统计 products 的订单销售额");

        assertThat(result.view().mode()).isEqualTo("RAG");
        assertThat(result.view().retrievedTables()).contains("orders", "products", "order_items");
        assertThat(result.view().promptTableCount()).isLessThanOrEqualTo(12);
    }

    @Test
    void shouldFallbackWhenVectorStoreIsUnavailable() {
        when(vectorIndex.available()).thenReturn(false);

        SchemaRetrievalResult result = retriever().retrieve(datasource, schema(50), "订单数量");

        assertThat(result.view().mode()).isEqualTo("FULL_SCHEMA");
        assertThat(result.view().fallback()).isTrue();
        assertThat(result.view().promptTableCount()).isEqualTo(50);
    }

    private SchemaRetriever retriever() {
        return new SchemaRetriever(vectorIndex, properties);
    }

    private DatasourceSchemaView schema(int count) {
        List<SchemaTableView> tables = new ArrayList<>();
        SchemaRelationView orderItemsToOrders = new SchemaRelationView(
                "fk_order", "demo.order_items", "order_id", "demo.orders", "id", "NO ACTION", "NO ACTION");
        SchemaRelationView orderItemsToProducts = new SchemaRelationView(
                "fk_product", "demo.order_items", "product_id", "demo.products", "id", "NO ACTION", "NO ACTION");
        tables.add(table(1, "orders", List.of()));
        tables.add(table(2, "products", List.of()));
        tables.add(table(3, "order_items", List.of(orderItemsToOrders, orderItemsToProducts)));
        for (int index = 4; index <= count; index++) {
            tables.add(table(index, "noise_" + index, List.of()));
        }
        return new DatasourceSchemaView(1L, List.copyOf(tables));
    }

    private SchemaTableView table(long id, String name, List<SchemaRelationView> relations) {
        return new SchemaTableView(id, "demo", name, "TABLE", name, List.of(), relations);
    }
}
