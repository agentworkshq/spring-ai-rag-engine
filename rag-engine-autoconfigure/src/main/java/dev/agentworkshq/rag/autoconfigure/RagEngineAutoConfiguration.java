package dev.agentworkshq.rag.autoconfigure;

import dev.agentworkshq.rag.core.ingest.ChunkingStrategy;
import dev.agentworkshq.rag.core.ingest.DefaultDocumentLoader;
import dev.agentworkshq.rag.core.ingest.DocumentLoader;
import dev.agentworkshq.rag.core.ingest.FixedTokenChunker;
import dev.agentworkshq.rag.core.ingest.RecursiveChunker;
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

    @Bean
    @ConditionalOnMissingBean
    ChunkingStrategy chunkingStrategy(RagEngineProperties properties) {
        RagEngineProperties.Chunking chunking = properties.chunking();
        return switch (chunking.strategy()) {
            case FIXED -> new FixedTokenChunker(chunking.chunkSize());
            case RECURSIVE -> new RecursiveChunker(chunking.chunkSize(), chunking.overlap());
        };
    }

    @Bean
    @ConditionalOnMissingBean
    DocumentLoader documentLoader(RagEngineProperties properties) {
        return new DefaultDocumentLoader(properties.limits().maxFileSize().toBytes(), properties.limits().maxPages());
    }
}
