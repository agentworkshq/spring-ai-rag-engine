package dev.agentworkshq.rag.web.security;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.UnauthorizedException;
import dev.agentworkshq.rag.web.RagWebProperties;
import jakarta.servlet.http.HttpServletRequest;

public class RequestGuard {

    private final RagWebProperties properties;

    public RequestGuard(RagWebProperties properties) {
        this.properties = properties;
    }

    public void requireAuthenticated(HttpServletRequest request) {
        if (properties.requireAuthentication() && request.getUserPrincipal() == null) {
            throw new UnauthorizedException(ErrorMessages.RAG_E004);
        }
    }

    public void requireAdmin(HttpServletRequest request) {
        requireAuthenticated(request);
        if (properties.adminRoleRequired() && !request.isUserInRole(properties.adminRole())) {
            throw new UnauthorizedException(ErrorMessages.RAG_E024);
        }
    }
}
