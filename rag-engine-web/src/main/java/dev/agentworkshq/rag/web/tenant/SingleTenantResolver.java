package dev.agentworkshq.rag.web.tenant;

import jakarta.servlet.http.HttpServletRequest;

public class SingleTenantResolver implements TenantResolver {

    private final String tenantId;

    public SingleTenantResolver(String tenantId) {
        this.tenantId = tenantId;
    }

    @Override
    public String resolve(HttpServletRequest request) {
        return tenantId;
    }
}
