package com.ai.indexing;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/** 来源、任务均以游标分页；批量请求只入持久队列，不在 HTTP 内执行外部向量调用。 */
@RestController
@RequestMapping("/api/v1/rag/indexes")
@PreAuthorize("hasRole('ADMIN')")
public class IndexRebuildController {
    private final IndexRebuildService service;
    private final RagIndexMaintenance maintenance;
    public IndexRebuildController(IndexRebuildService service, RagIndexMaintenance maintenance) {
        this.service = service;
        this.maintenance = maintenance;
    }

    @GetMapping("/maintenance")
    public boolean maintenance() { return maintenance.isEnabled(); }

    @GetMapping("/sources")
    public IndexRebuildService.Page<IndexRebuildService.Source> sources(@RequestParam IndexTask.Kind kind,
            @RequestParam(defaultValue = "") String after, @RequestParam(defaultValue = "50") int limit) {
        return service.sources(kind, after, limit);
    }

    @GetMapping("/tasks")
    public IndexRebuildService.Page<IndexRebuildService.TaskView> tasks(@RequestParam IndexTask.Kind kind,
            @RequestParam(defaultValue = "") String after, @RequestParam(defaultValue = "50") int limit) {
        return service.taskPage(kind, after, limit);
    }

    @PostMapping("/rebuild")
    public List<String> rebuild(@RequestBody RebuildRequest request) {
        return service.rebuild(request.kind(), request.sourceIds());
    }

    @PostMapping("/{kind}/{sourceId}/retry")
    public boolean retry(@PathVariable IndexTask.Kind kind, @PathVariable String sourceId) {
        return service.retry(kind, sourceId);
    }

    public record RebuildRequest(IndexTask.Kind kind, List<String> sourceIds) { }
}
