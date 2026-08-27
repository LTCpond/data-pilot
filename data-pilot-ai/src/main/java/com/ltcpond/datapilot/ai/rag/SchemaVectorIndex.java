package com.ltcpond.datapilot.ai.rag;

import java.util.List;

/** Schema 向量存储边界，使核心流程不依赖具体 VectorStore。 */
public interface SchemaVectorIndex {

    /** 写入同一索引版本下的一批 Schema 向量文档。 */
    void index(List<SchemaVectorDocument> documents);

    /** 在指定数据源和索引版本内检索与问题最相关的表。 */
    List<SchemaVectorMatch> search(long datasourceId, String indexVersion, String question, int topK);

    /** 删除指定数据源的某个索引版本，通常用于清理失败写入。 */
    void deleteVersion(long datasourceId, String indexVersion);

    /** 删除指定数据源除当前活动版本外的历史索引版本。 */
    void deleteOtherVersions(long datasourceId, String activeIndexVersion);

    /** 判断底层向量存储和访问依赖是否已经装配可用。 */
    boolean available();
}
