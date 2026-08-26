package com.ltcpond.datapilot.core.datasource;

public class DuplicateDatasourceException extends RuntimeException {

    public DuplicateDatasourceException() {
        super("Datasource name already exists");
    }
}
