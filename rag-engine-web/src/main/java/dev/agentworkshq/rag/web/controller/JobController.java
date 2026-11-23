package dev.agentworkshq.rag.web.controller;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.ResourceNotFoundException;
import dev.agentworkshq.rag.core.store.IngestionJobStore;
import dev.agentworkshq.rag.web.dto.ApiResponse;
import dev.agentworkshq.rag.web.dto.JobResponse;
import dev.agentworkshq.rag.web.security.RequestGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class JobController implements RagControllers {

    private final IngestionJobStore jobStore;
    private final RequestGuard guard;

    public JobController(IngestionJobStore jobStore, RequestGuard guard) {
        this.jobStore = jobStore;
        this.guard = guard;
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<ApiResponse<JobResponse>> job(@PathVariable Long id, HttpServletRequest request) {
        guard.requireAdmin(request);
        return ResponseEntity.ok(ApiResponse.success("Job retrieved successfully",
                jobStore.findById(id).map(JobResponse::from)
                        .orElseThrow(() -> new ResourceNotFoundException(ErrorMessages.RAG_E001))));
    }
}
