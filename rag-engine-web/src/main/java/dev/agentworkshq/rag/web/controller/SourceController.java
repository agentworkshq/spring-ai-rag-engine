package dev.agentworkshq.rag.web.controller;

import dev.agentworkshq.rag.core.ingest.IngestionService;
import dev.agentworkshq.rag.core.store.IngestionJobStore;
import dev.agentworkshq.rag.web.dto.ApiResponse;
import dev.agentworkshq.rag.web.dto.IngestionResultResponse;
import dev.agentworkshq.rag.web.dto.JobResponse;
import dev.agentworkshq.rag.web.security.RequestGuard;
import dev.agentworkshq.rag.web.tenant.TenantContext;
import dev.agentworkshq.rag.web.tenant.TenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SourceController implements RagControllers {

    private static final String NAME_PATTERN = "[A-Za-z0-9._-]+";

    private final IngestionService ingestionService;
    private final IngestionJobStore jobStore;
    private final TenantResolver tenantResolver;
    private final RequestGuard guard;

    public SourceController(IngestionService ingestionService, IngestionJobStore jobStore,
                            TenantResolver tenantResolver, RequestGuard guard) {
        this.ingestionService = ingestionService;
        this.jobStore = jobStore;
        this.tenantResolver = tenantResolver;
        this.guard = guard;
    }

    @PostMapping("/sources/{name}/ingest")
    public ResponseEntity<ApiResponse<IngestionResultResponse>> ingest(
            @PathVariable @Size(min = 1, max = 100) @Pattern(regexp = NAME_PATTERN) String name,
            HttpServletRequest request) {
        guard.requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.success("Ingestion finished",
                IngestionResultResponse.from(ingestionService.ingest(name,
                        TenantContext.require(tenantResolver, request)))));
    }

    @GetMapping("/sources/{name}/jobs")
    public ResponseEntity<ApiResponse<List<JobResponse>>> jobs(
            @PathVariable @Size(min = 1, max = 100) @Pattern(regexp = NAME_PATTERN) String name,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit,
            HttpServletRequest request) {
        guard.requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.success("Jobs retrieved successfully",
                jobStore.findRecent(name, limit).stream().map(JobResponse::from).toList()));
    }
}
