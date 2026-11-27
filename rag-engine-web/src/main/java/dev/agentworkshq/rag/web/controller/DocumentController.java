package dev.agentworkshq.rag.web.controller;

import dev.agentworkshq.rag.core.ingest.DocumentIngestResult;
import dev.agentworkshq.rag.core.store.SourceDocumentRecord;
import dev.agentworkshq.rag.web.dto.ApiResponse;
import dev.agentworkshq.rag.web.dto.DocumentResponse;
import dev.agentworkshq.rag.web.dto.PageMetadata;
import dev.agentworkshq.rag.web.security.RequestGuard;
import dev.agentworkshq.rag.web.tenant.TenantContext;
import dev.agentworkshq.rag.web.tenant.TenantResolver;
import dev.agentworkshq.rag.web.upload.UploadService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

@RestController
public class DocumentController implements RagControllers {

    private final UploadService uploads;
    private final TenantResolver tenantResolver;
    private final RequestGuard guard;

    public DocumentController(UploadService uploads, TenantResolver tenantResolver, RequestGuard guard) {
        this.uploads = uploads;
        this.tenantResolver = tenantResolver;
        this.guard = guard;
    }

    @PostMapping(path = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentResponse>> upload(@RequestParam("file") MultipartFile file,
                                                                HttpServletRequest request) {
        guard.requireUploadAccess(request);
        DocumentIngestResult result = uploads.upload(file, TenantContext.require(tenantResolver, request));
        DocumentResponse body = DocumentResponse.from(result);
        if (result.outcome() == DocumentIngestResult.Outcome.ADDED) {
            URI location = URI.create(request.getRequestURI() + "/"
                    + UriUtils.encodePathSegment(result.externalId(), StandardCharsets.UTF_8));
            return ResponseEntity.created(location).body(ApiResponse.success("Document ingested", body));
        }
        return ResponseEntity.ok(ApiResponse.success("Document ingested", body));
    }

    @GetMapping("/documents")
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            HttpServletRequest request) {
        guard.requireUploadAccess(request);
        List<SourceDocumentRecord> all = uploads.list(TenantContext.require(tenantResolver, request));
        int from = (int) Math.min((long) page * size, all.size());
        List<DocumentResponse> items = all.subList(from, Math.min(all.size(), from + size)).stream()
                .map(DocumentResponse::from).toList();
        int totalPages = (all.size() + size - 1) / size;
        return ResponseEntity.ok(ApiResponse.success("Documents retrieved successfully", items,
                new PageMetadata(page, size, totalPages, all.size())));
    }

    @GetMapping("/documents/{externalId}")
    public ResponseEntity<ApiResponse<DocumentResponse>> get(@PathVariable String externalId,
                                                             HttpServletRequest request) {
        guard.requireUploadAccess(request);
        SourceDocumentRecord record = uploads.get(externalId, TenantContext.require(tenantResolver, request));
        return ResponseEntity.ok(ApiResponse.success("Document retrieved successfully",
                DocumentResponse.from(record)));
    }

    @DeleteMapping("/documents/{externalId}")
    public ResponseEntity<Void> delete(@PathVariable String externalId, HttpServletRequest request) {
        guard.requireUploadAccess(request);
        uploads.delete(externalId, TenantContext.require(tenantResolver, request));
        return ResponseEntity.noContent().build();
    }
}
