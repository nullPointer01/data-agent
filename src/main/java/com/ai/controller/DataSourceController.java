package com.ai.controller;

import com.ai.datasource.dto.DataSourceListResponse;
import com.ai.datasource.dto.DataSourceMutationResponse;
import com.ai.datasource.dto.DataSourcePreviewResponse;
import com.ai.datasource.dto.DataSourceRequest;
import com.ai.datasource.dto.DataSourceSchemaResponse;
import com.ai.datasource.dto.DataSourceTestResponse;
import com.ai.service.connector.DataConnectorService;
import com.ai.service.connector.DataSourceService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据源管理接口。
 *
 * @author data-agent
 */
@RestController
@RequestMapping("/api/v1/datasources")
public class DataSourceController {

    private final DataSourceService dataSourceService;
    private final DataConnectorService dataConnectorService;

    public DataSourceController(DataSourceService dataSourceService,
            DataConnectorService dataConnectorService) {
        this.dataSourceService = dataSourceService;
        this.dataConnectorService = dataConnectorService;
    }

    @GetMapping("/list")
    public DataSourceListResponse list() {
        return dataSourceService.list();
    }

    @PostMapping("/add")
    public DataSourceMutationResponse add(@RequestBody DataSourceRequest request) {
        return dataSourceService.create(request);
    }

    @PutMapping("/update/{id}")
    public DataSourceMutationResponse update(@PathVariable String id, @RequestBody DataSourceRequest request) {
        return dataSourceService.update(id, request);
    }

    @DeleteMapping("/delete/{id}")
    public DataSourceMutationResponse delete(@PathVariable String id) {
        return dataSourceService.delete(id);
    }

    @PutMapping("/toggle/{id}")
    public DataSourceMutationResponse toggle(@PathVariable String id) {
        return dataSourceService.toggle(id);
    }

    @PostMapping("/test/{id}")
    public DataSourceTestResponse test(@PathVariable String id) {
        String result = dataConnectorService.test(id);
        return new DataSourceTestResponse(result.startsWith("连接成功"), result);
    }

    @GetMapping("/schema/{id}")
    public DataSourceSchemaResponse schema(@PathVariable String id) {
        return new DataSourceSchemaResponse(true, dataConnectorService.schema(id));
    }

    @GetMapping("/preview/{id}")
    public DataSourcePreviewResponse preview(@PathVariable String id,
            @RequestParam(defaultValue = "50") int limit) {
        return new DataSourcePreviewResponse(true, dataConnectorService.preview(id, limit));
    }
}
