package dev.agentworkshq.rag.web.controller;

import dev.agentworkshq.rag.core.answer.RagContext;
import dev.agentworkshq.rag.core.answer.RagService;
import dev.agentworkshq.rag.web.dto.ApiResponse;
import dev.agentworkshq.rag.web.dto.AskRequest;
import dev.agentworkshq.rag.web.dto.AskResponse;
import dev.agentworkshq.rag.web.security.RequestGuard;
import dev.agentworkshq.rag.web.tenant.TenantContext;
import dev.agentworkshq.rag.web.tenant.TenantResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AskController implements RagControllers {

    private final RagService ragService;
    private final TenantResolver tenantResolver;
    private final RequestGuard guard;

    public AskController(RagService ragService, TenantResolver tenantResolver, RequestGuard guard) {
        this.ragService = ragService;
        this.tenantResolver = tenantResolver;
        this.guard = guard;
    }

    @PostMapping("/ask")
    public ResponseEntity<ApiResponse<AskResponse>> ask(@Valid @RequestBody AskRequest body,
                                                        HttpServletRequest request) {
        guard.requireAuthenticated(request);
        RagContext context = new RagContext(TenantContext.require(tenantResolver, request), body.sourceNames(),
                body.topK());
        return ResponseEntity.ok(ApiResponse.success("Answer generated successfully",
                AskResponse.from(ragService.ask(body.question(), context))));
    }
}
