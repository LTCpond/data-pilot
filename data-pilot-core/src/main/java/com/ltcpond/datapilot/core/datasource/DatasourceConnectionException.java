package com.ltcpond.datapilot.core.datasource;

public class DatasourceConnectionException extends RuntimeException {

    public DatasourceConnectionException() {
        super("Datasource is unreachable");
    }
}
