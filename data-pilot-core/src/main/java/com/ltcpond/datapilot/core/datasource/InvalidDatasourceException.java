package com.ltcpond.datapilot.core.datasource;

public class InvalidDatasourceException extends RuntimeException {

    public InvalidDatasourceException() {
        super("Invalid datasource configuration");
    }
}
