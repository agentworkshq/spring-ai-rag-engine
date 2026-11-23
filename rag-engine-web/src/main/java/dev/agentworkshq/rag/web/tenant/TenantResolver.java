package dev.agentworkshq.rag.web.tenant;

import jakarta.servlet.http.HttpServletRequest;

public interface TenantResolver {

    String resolve(HttpServletRequest request);
}
