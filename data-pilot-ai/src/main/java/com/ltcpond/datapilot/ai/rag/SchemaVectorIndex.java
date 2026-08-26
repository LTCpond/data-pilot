package com.ltcpond.datapilot.ai.rag;

import java.util.List;

/** Schema 向量存储边界，使核心流程不依赖具体 VectorStore。 */
public interface SchemaVectorIndex {

    void index(List<SchemaVectorDocument> documents);

    List<SchemaVectorMatch> search(long datasourceId, String indexVersion, String question, int topK);

    void deleteVersion(long datasourceId, String indexVersion);

    void deleteOtherVersions(long datasourceId, String activeIndexVersion);

    boolean available();
}
