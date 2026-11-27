package dev.agentworkshq.rag.web;

import dev.agentworkshq.rag.core.answer.RagService;
import dev.agentworkshq.rag.core.ingest.IngestionLimits;
import dev.agentworkshq.rag.core.ingest.IngestionService;
import dev.agentworkshq.rag.core.store.IngestionJobStore;
import dev.agentworkshq.rag.web.controller.AskController;
import dev.agentworkshq.rag.web.controller.DocumentController;
import dev.agentworkshq.rag.web.controller.JobController;
import dev.agentworkshq.rag.web.controller.RagControllers;
import dev.agentworkshq.rag.web.controller.SourceController;
import dev.agentworkshq.rag.web.exception.RagExceptionHandler;
import dev.agentworkshq.rag.web.security.RequestGuard;
import dev.agentworkshq.rag.web.tenant.SingleTenantResolver;
import dev.agentworkshq.rag.web.tenant.TenantResolver;
import dev.agentworkshq.rag.web.upload.UploadService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "rag-engine", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnProperty(prefix = "rag-engine.web", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(RagWebProperties.class)
public class RagWebAutoConfiguration {

    @Bean
    RagExceptionHandler ragExceptionHandler() {
        return new RagExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    TenantResolver tenantResolver(RagWebProperties properties) {
        if (properties.tenant().mode() == RagWebProperties.Tenant.Mode.CUSTOM) {
            throw new IllegalStateException(
                    "rag-engine.web.tenant.mode=CUSTOM requires a TenantResolver bean, but none was found");
        }
        return new SingleTenantResolver(properties.tenant().defaultTenant());
    }

    @Bean
    RequestGuard ragRequestGuard(RagWebProperties properties) {
        return new RequestGuard(properties);
    }

    @Bean
    WebMvcConfigurer ragWebMvcConfigurer(RagWebProperties properties) {
        return new WebMvcConfigurer() {
            @Override
            public void configurePathMatch(PathMatchConfigurer configurer) {
                configurer.addPathPrefix(properties.basePath(), RagControllers.class::isAssignableFrom);
            }
        };
    }

    @Bean
    AskController askController(RagService ragService, TenantResolver tenantResolver, RequestGuard guard) {
        return new AskController(ragService, tenantResolver, guard);
    }

    @Bean
    SourceController sourceController(IngestionService ingestionService, IngestionJobStore jobStore,
                                      TenantResolver tenantResolver, RequestGuard guard) {
        return new SourceController(ingestionService, jobStore, tenantResolver, guard);
    }

    @Bean
    JobController jobController(IngestionJobStore jobStore, RequestGuard guard) {
        return new JobController(jobStore, guard);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(prefix = "rag-engine.web.upload", name = "enabled", havingValue = "true")
    static class UploadConfiguration {

        @Bean
        UploadService uploadService(IngestionService ingestionService, IngestionLimits limits,
                                    RagWebProperties properties) {
            return new UploadService(ingestionService, limits, properties.upload());
        }

        @Bean
        DocumentController documentController(UploadService uploadService, TenantResolver tenantResolver,
                                              RequestGuard guard) {
            return new DocumentController(uploadService, tenantResolver, guard);
        }
    }
}
