package com.ltcpond.datapilot.core.datasource;

public class MetadataSyncException extends RuntimeException {

    public MetadataSyncException() {
        super("Datasource metadata synchronization failed");
    }
}
