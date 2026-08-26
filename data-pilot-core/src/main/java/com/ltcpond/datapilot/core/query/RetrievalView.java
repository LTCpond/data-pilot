package com.ltcpond.datapilot.core.query;

import java.util.List;

/** 本次问数使用的 Schema 召回信息。 */
public record RetrievalView(
        String mode,
        boolean fallback,
        int totalTableCount,
        int promptTableCount,
        List<String> retrievedTables,
        long durationMs) {
}
