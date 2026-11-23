package dev.agentworkshq.rag.autoconfigure;

import dev.agentworkshq.rag.core.answer.NoopReranker;
import dev.agentworkshq.rag.core.answer.RagService;
import dev.agentworkshq.rag.core.answer.RagSettings;
import dev.agentworkshq.rag.core.answer.Reranker;
import dev.agentworkshq.rag.core.ingest.ChunkingStrategy;
import dev.agentworkshq.rag.core.ingest.DefaultDocumentLoader;
import dev.agentworkshq.rag.core.ingest.DocumentLoader;
import dev.agentworkshq.rag.core.ingest.FixedTokenChunker;
import dev.agentworkshq.rag.core.ingest.FilesystemSource;
import dev.agentworkshq.rag.core.ingest.IngestionPipeline;
import dev.agentworkshq.rag.core.ingest.IngestionService;
import dev.agentworkshq.rag.core.ingest.RagSource;
import dev.agentworkshq.rag.core.ingest.RecursiveChunker;
import dev.agentworkshq.rag.core.ingest.SourceRegistry;
import dev.agentworkshq.rag.core.store.DocumentRegistry;
import dev.agentworkshq.rag.core.store.InMemoryDocumentRegistry;
import dev.agentworkshq.rag.core.store.InMemoryIngestionJobStore;
import dev.agentworkshq.rag.core.store.IngestionJobStore;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
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

    @Bean
    @ConditionalOnMissingBean
    SourceRegistry sourceRegistry(RagEngineProperties properties, ObjectProvider<RagSource> beans) {
        List<RagSource> sources = new ArrayList<>();
        for (RagEngineProperties.Source source : properties.sources()) {
            sources.add(new FilesystemSource(source.name(), Path.of(source.path()), source.include()));
        }
        beans.orderedStream().forEach(sources::add);
        return new SourceRegistry(sources);
    }

    @Bean
    @ConditionalOnMissingBean
    IngestionPipeline ingestionPipeline(ObjectProvider<VectorStore> vectorStore, DocumentLoader documentLoader,
                                        ChunkingStrategy chunkingStrategy, DocumentRegistry documentRegistry,
                                        IngestionJobStore ingestionJobStore, RagEngineProperties properties) {
        RagEngineProperties.Chunking chunking = properties.chunking();
        String signature = chunking.strategy() == RagEngineProperties.Chunking.Strategy.FIXED
                ? "FIXED:" + chunking.chunkSize()
                : "RECURSIVE:" + chunking.chunkSize() + ":" + chunking.overlap();
        return new IngestionPipeline(vectorStore::getIfAvailable, documentLoader, chunkingStrategy,
                documentRegistry, ingestionJobStore, signature, properties.ingestion().batchSize(),
                properties.ingestion().allowEmptySource());
    }

    @Bean
    @ConditionalOnMissingBean
    IngestionService ingestionService(SourceRegistry sourceRegistry, IngestionPipeline ingestionPipeline) {
        return new IngestionService(sourceRegistry, ingestionPipeline);
    }

    @Bean
    @ConditionalOnMissingBean
    Reranker reranker() {
        return new NoopReranker();
    }

    @Bean
    @ConditionalOnMissingBean
    RagService ragService(ObjectProvider<VectorStore> vectorStore, ObjectProvider<ChatClient.Builder> chatClient,
                          Reranker reranker, RagEngineProperties properties) {
        RagEngineProperties.Retrieval retrieval = properties.retrieval();
        RagEngineProperties.Answer answer = properties.answer();
        RagSettings settings = new RagSettings(retrieval.topK(), retrieval.maxTopK(), retrieval.similarityThreshold(),
                retrieval.queryRewrite(), answer.citeSources(), answer.noAnswerMessage(), answer.maxContextChars(),
                answer.maxQuestionLength());
        return new RagService(vectorStore::getIfAvailable, chatClient::getIfAvailable, reranker, settings);
    }
}
