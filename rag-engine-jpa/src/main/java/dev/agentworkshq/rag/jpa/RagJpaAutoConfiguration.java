package dev.agentworkshq.rag.jpa;

import dev.agentworkshq.rag.core.store.DocumentRegistry;
import dev.agentworkshq.rag.core.store.IngestionJobStore;
import dev.agentworkshq.rag.jpa.repository.IngestionJobRepository;
import dev.agentworkshq.rag.jpa.repository.SourceDocumentRepository;
import dev.agentworkshq.rag.jpa.store.JpaDocumentRegistry;
import dev.agentworkshq.rag.jpa.store.JpaIngestionJobStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(beforeName = "dev.agentworkshq.rag.autoconfigure.RagEngineAutoConfiguration")
@AutoConfigurationPackage
@ConditionalOnProperty(prefix = "rag-engine", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RagJpaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    DocumentRegistry documentRegistry(SourceDocumentRepository repository) {
        return new JpaDocumentRegistry(repository);
    }

    @Bean
    @ConditionalOnMissingBean
    IngestionJobStore ingestionJobStore(IngestionJobRepository repository) {
        return new JpaIngestionJobStore(repository);
    }
}
