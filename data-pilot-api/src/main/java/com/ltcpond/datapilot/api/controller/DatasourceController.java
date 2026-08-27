package com.ltcpond.datapilot.api.controller;

import com.ltcpond.datapilot.api.request.ConnectionTestRequest;
import com.ltcpond.datapilot.api.request.DatasourceRequest;
import com.ltcpond.datapilot.common.api.ApiResponse;
import com.ltcpond.datapilot.core.datasource.ConnectionTestCommand;
import com.ltcpond.datapilot.core.datasource.ConnectionTestView;
import com.ltcpond.datapilot.core.datasource.DatasourceCommand;
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

    /** 使用调用方提交的连接信息执行一次临时连通性测试，不持久化密码。 */
    @PostMapping("/test-connection")
    public ApiResponse<ConnectionTestView> testConnection(@Valid @RequestBody ConnectionTestRequest request) {
        ConnectionTestView result = datasourceService.testConnection(new ConnectionTestCommand(
                request.jdbcUrl(), request.username(), request.password()));
        return ApiResponse.success(result);
    }

    /** 创建数据源配置；保存前会先验证连接并加密持久化密码。 */
    @PostMapping
    public ResponseEntity<ApiResponse<DatasourceView>> create(
            @Valid @RequestBody DatasourceRequest request) {
        DatasourceView created = datasourceService.create(new DatasourceCommand(
                request.name(),
                request.description(),
                request.jdbcUrl(),
                request.username(),
                request.password()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    /** 按 ID 顺序列出已配置的数据源。 */
    @GetMapping
    public ApiResponse<List<DatasourceView>> list() {
        return ApiResponse.success(datasourceService.list());
    }

    /** 读取单个数据源的安全视图，不返回明文密码。 */
    @GetMapping("/{datasourceId}")
    public ApiResponse<DatasourceView> get(@PathVariable long datasourceId) {
        return ApiResponse.success(datasourceService.get(datasourceId));
    }

    /** 同步业务库元数据，并尽力触发 Schema 向量索引重建。 */
    @PostMapping("/{datasourceId}/sync")
    public ApiResponse<SyncResultView> synchronize(@PathVariable long datasourceId) {
        return ApiResponse.success(datasourceService.synchronize(datasourceId));
    }

    /** 返回最近一次同步后的表、字段和外键关系快照。 */
    @GetMapping("/{datasourceId}/schema")
    public ApiResponse<DatasourceSchemaView> schema(@PathVariable long datasourceId) {
        return ApiResponse.success(datasourceService.schema(datasourceId));
    }

    /** 手动重建数据源 Schema 的 RAG 向量索引。 */
    @PostMapping("/{datasourceId}/rag-index")
    public ApiResponse<RagIndexResultView> rebuildRagIndex(@PathVariable long datasourceId) {
        return ApiResponse.success(schemaIndexService.rebuild(datasourceId));
    }
}
