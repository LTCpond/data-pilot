package com.ltcpond.datapilot.api;

import com.ltcpond.datapilot.api.request.ConnectionTestRequest;
import com.ltcpond.datapilot.api.request.CreateDatasourceRequest;
import com.ltcpond.datapilot.common.api.ApiResponse;
import com.ltcpond.datapilot.core.datasource.ConnectionTestCommand;
import com.ltcpond.datapilot.core.datasource.ConnectionTestView;
import com.ltcpond.datapilot.core.datasource.CreateDatasourceCommand;
import com.ltcpond.datapilot.core.datasource.DatasourceSchemaView;
import com.ltcpond.datapilot.core.datasource.DatasourceService;
import com.ltcpond.datapilot.core.datasource.DatasourceView;
import com.ltcpond.datapilot.core.datasource.SyncResultView;
import com.ltcpond.datapilot.core.rag.RagIndexResultView;
import com.ltcpond.datapilot.core.rag.SchemaIndexService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** MySQL 数据源管理和 Schema 同步接口。 */
@RestController
@RequestMapping("/api/datasources")
@RequiredArgsConstructor
public class DatasourceController {

    private final DatasourceService datasourceService;
    private final SchemaIndexService schemaIndexService;

    @PostMapping("/test-connection")
    public ApiResponse<ConnectionTestView> testConnection(@Valid @RequestBody ConnectionTestRequest request) {
        ConnectionTestView result = datasourceService.testConnection(new ConnectionTestCommand(
                request.jdbcUrl(), request.username(), request.password()));
        return ApiResponse.success(result);
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DatasourceView>> create(
            @Valid @RequestBody CreateDatasourceRequest request) {
        DatasourceView created = datasourceService.create(new CreateDatasourceCommand(
                request.name(),
                request.description(),
                request.jdbcUrl(),
                request.username(),
                request.password()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @GetMapping
    public ApiResponse<List<DatasourceView>> list() {
        return ApiResponse.success(datasourceService.list());
    }

    @GetMapping("/{datasourceId}")
    public ApiResponse<DatasourceView> get(@PathVariable long datasourceId) {
        return ApiResponse.success(datasourceService.get(datasourceId));
    }

    @PostMapping("/{datasourceId}/sync")
    public ApiResponse<SyncResultView> synchronize(@PathVariable long datasourceId) {
        return ApiResponse.success(datasourceService.synchronize(datasourceId));
    }

    @GetMapping("/{datasourceId}/schema")
    public ApiResponse<DatasourceSchemaView> schema(@PathVariable long datasourceId) {
        return ApiResponse.success(datasourceService.schema(datasourceId));
    }

    @PostMapping("/{datasourceId}/rag-index")
    public ApiResponse<RagIndexResultView> rebuildRagIndex(@PathVariable long datasourceId) {
        return ApiResponse.success(schemaIndexService.rebuild(datasourceId));
    }
}
