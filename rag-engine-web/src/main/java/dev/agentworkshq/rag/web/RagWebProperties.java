package dev.agentworkshq.rag.web;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "rag-engine.web")
public record RagWebProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("/api/rag") String basePath,
        @DefaultValue("true") boolean requireAuthentication,
        @DefaultValue("RAG_ADMIN") String adminRole,
        @DefaultValue Tenant tenant,
        @DefaultValue Upload upload) {

    public RagWebProperties {
        if (basePath == null || !basePath.startsWith("/") || basePath.endsWith("/")) {
            throw new IllegalArgumentException(
                    "rag-engine.web.base-path must start with '/' and must not end with '/'");
        }
    }

    public boolean adminRoleRequired() {
        return requireAuthentication && adminRole != null && !adminRole.isBlank();
    }

    @Override
    public String toString() {
        return "RagWebProperties[]";
    }

    public record Upload(
            @DefaultValue("false") boolean enabled,
            @DefaultValue("true") boolean requireAdmin,
            @DefaultValue("1000") int maxDocumentsPerTenant) {

        public Upload {
            if (maxDocumentsPerTenant <= 0) {
                throw new IllegalArgumentException("rag-engine.web.upload.max-documents-per-tenant must be positive");
            }
        }

        @Override
        public String toString() {
            return "Upload[]";
        }
    }

    public record Tenant(
            @DefaultValue("SINGLE") Mode mode,
            @DefaultValue("default") String defaultTenant) {

        public Tenant {
            if (defaultTenant == null || defaultTenant.isBlank()) {
                throw new IllegalArgumentException("rag-engine.web.tenant.default-tenant must not be blank");
            }
        }

        @Override
        public String toString() {
            return "Tenant[]";
        }

        public enum Mode {
            SINGLE, CUSTOM
        }
    }
}
