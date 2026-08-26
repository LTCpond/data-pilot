package com.ltcpond.datapilot.core.datasource;

public class DatasourceNotFoundException extends RuntimeException {

    public DatasourceNotFoundException() {
        super("Datasource not found");
    }
}
