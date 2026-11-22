package dev.agentworkshq.rag.autoconfigure;

import dev.agentworkshq.rag.core.store.DocumentRegistry;
import dev.agentworkshq.rag.core.store.InMemoryDocumentRegistry;
import dev.agentworkshq.rag.core.store.InMemoryIngestionJobStore;
import dev.agentworkshq.rag.core.store.IngestionJobStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(RagEngineProperties.class)
public class RagEngineAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    DocumentRegistry documentRegistry() {
        return new InMemoryDocumentRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    IngestionJobStore ingestionJobStore() {
        return new InMemoryIngestionJobStore();
    }
}
