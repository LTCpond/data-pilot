package com.ltcpond.datapilot.core.rag;

import com.ltcpond.datapilot.ai.rag.SchemaVectorDocument;
import com.ltcpond.datapilot.core.datasource.DatasourceSchemaView;
import com.ltcpond.datapilot.core.datasource.SchemaColumnView;
import com.ltcpond.datapilot.core.datasource.SchemaRelationView;
import com.ltcpond.datapilot.core.datasource.SchemaTableView;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaDocumentBuilderTest {

    @Test
    void shouldBuildOneSafeDocumentPerTableWithBidirectionalRelations() {
        SchemaColumnView id = new SchemaColumnView(1L, "id", Types.BIGINT, "BIGINT", 1,
                false, true, "订单ID");
        SchemaRelationView relation = new SchemaRelationView(
                "fk_order", "demo.order_items", "order_id", "demo.orders", "id", "NO ACTION", "CASCADE");
        DatasourceSchemaView schema = new DatasourceSchemaView(7L, List.of(
                new SchemaTableView(1L, "demo", "orders", "TABLE", "订单表", List.of(id), List.of()),
                new SchemaTableView(2L, "demo", "order_items", "TABLE", "订单明细表", List.of(id), List.of(relation))));

        List<SchemaVectorDocument> documents = new SchemaDocumentBuilder().build(7L, "version-1", schema);

        assertThat(documents).hasSize(2);
        assertThat(documents.getFirst().content()).contains("订单表", "入向", "order_items")
                .doesNotContain("jdbc:mysql", "123456", "password");
        assertThat(documents.get(1).content()).contains("出向", "orders");
    }
}
