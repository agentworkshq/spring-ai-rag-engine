package dev.agentworkshq.rag.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import dev.agentworkshq.rag.core.answer.NoopReranker;
import dev.agentworkshq.rag.core.answer.RagService;
import dev.agentworkshq.rag.core.answer.RagSettings;
import dev.agentworkshq.rag.core.ingest.DefaultDocumentLoader;
import dev.agentworkshq.rag.core.ingest.FilesystemSource;
import dev.agentworkshq.rag.core.ingest.IngestionLimits;
import dev.agentworkshq.rag.core.ingest.IngestionPipeline;
import dev.agentworkshq.rag.core.ingest.IngestionService;
import dev.agentworkshq.rag.core.ingest.RagSource;
import dev.agentworkshq.rag.core.ingest.RecursiveChunker;
import dev.agentworkshq.rag.core.ingest.SourceItem;
import dev.agentworkshq.rag.core.ingest.SourceRegistry;
import dev.agentworkshq.rag.core.store.InMemoryDocumentRegistry;
import dev.agentworkshq.rag.core.store.InMemoryIngestionJobStore;
import dev.agentworkshq.rag.core.store.SourceDocumentRecord;
import dev.agentworkshq.rag.web.controller.AskController;
import dev.agentworkshq.rag.web.controller.DocumentController;
import dev.agentworkshq.rag.web.controller.SourceController;
import dev.agentworkshq.rag.web.exception.RagExceptionHandler;
import dev.agentworkshq.rag.web.security.RequestGuard;
import dev.agentworkshq.rag.web.tenant.SingleTenantResolver;
import dev.agentworkshq.rag.web.tenant.TenantResolver;
import dev.agentworkshq.rag.web.upload.UploadService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LogPrivacyTest {

    private static final String MARKER = "SECRET-MARKER-TEXT";

    @TempDir
    Path root;

    private final VectorStore store = mock(VectorStore.class);
    private final InMemoryDocumentRegistry registry = new InMemoryDocumentRegistry();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger rootLogger = (Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    private Level previousLevel;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws IOException {
        Files.writeString(root.resolve(MARKER + ".txt"), "content");
        RagSource broken = new RagSource() {
            @Override
            public String name() {
                return "broken";
            }

            @Override
            public Stream<SourceItem> scan() {
                throw new IllegalStateException(MARKER);
            }
        };

        IngestionPipeline pipeline = new IngestionPipeline(() -> store, new DefaultDocumentLoader(1024, 10),
                new RecursiveChunker(100, 10), registry, new InMemoryIngestionJobStore(), "RECURSIVE:100:10", 50,
                false, 1024);
        IngestionService ingestion = new IngestionService(
                new SourceRegistry(List.of(new FilesystemSource(MARKER, root, List.of()), broken)), pipeline);
        RagService rag = new RagService(() -> store, () -> mock(ChatClient.Builder.class), new NoopReranker(),
                new RagSettings(6, 20, 0.5, false, true, "none", 1000, 1000));
        RagWebProperties properties = new RagWebProperties(true, "/api/rag", false, "RAG_ADMIN",
                new RagWebProperties.Tenant(RagWebProperties.Tenant.Mode.SINGLE, MARKER),
                new RagWebProperties.Upload(true, true, 5));
        RequestGuard guard = new RequestGuard(properties);
        TenantResolver tenants = new SingleTenantResolver(MARKER);
        mvc = MockMvcBuilders.standaloneSetup(
                        new AskController(rag, tenants, guard),
                        new SourceController(ingestion, new InMemoryIngestionJobStore(), tenants, guard),
                        new DocumentController(new UploadService(ingestion,
                                new IngestionLimits(1024, 10), properties.upload()),
                                tenants, guard))
                .setControllerAdvice(new RagExceptionHandler())
                .build();

        previousLevel = rootLogger.getLevel();
        rootLogger.setLevel(Level.DEBUG);
        logs.start();
        rootLogger.addAppender(logs);
    }

    @AfterEach
    void restore() {
        rootLogger.detachAppender(logs);
        rootLogger.setLevel(previousLevel);
    }

    private static String everything(ILoggingEvent event) {
        StringBuilder text = new StringBuilder(event.getFormattedMessage());
        for (IThrowableProxy t = event.getThrowableProxy(); t != null; t = t.getCause()) {
            text.append(' ').append(t.getMessage());
        }
        return text.toString();
    }

    private List<String> messages(Level level) {
        return new ArrayList<>(logs.list).stream().filter(e -> e.getLevel() == level)
                .map(ILoggingEvent::getFormattedMessage).toList();
    }

    private void assertOnlyDebugCarriesTheMarker() {
        List<ILoggingEvent> events = new ArrayList<>(logs.list);
        assertThat(events.stream().filter(e -> e.getLevel().isGreaterOrEqual(Level.INFO)))
                .isNotEmpty()
                .allSatisfy(e -> assertThat(everything(e)).doesNotContain(MARKER));
        assertThat(events.stream().filter(e -> !e.getLevel().isGreaterOrEqual(Level.INFO)))
                .anySatisfy(e -> assertThat(everything(e)).contains(MARKER));
    }

    @Test
    void aFailingDocumentCleanupAndRemoval() throws Exception {
        registry.save(new SourceDocumentRecord(MARKER, MARKER + "-gone.txt", MARKER, "hash", 1));
        // the first delete clears the old chunks, every later one fails
        doNothing().doThrow(new IllegalStateException(MARKER))
                .when(store).delete(any(Filter.Expression.class));
        doThrow(new IllegalStateException(MARKER)).when(store).add(anyList());

        mvc.perform(post("/sources/" + MARKER + "/ingest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.failed").value(2));

        assertThat(messages(Level.WARN)).containsExactly(
                "Cleanup after a failed document failed: java.lang.IllegalStateException",
                "Ingestion of a document failed: java.lang.IllegalStateException",
                "Removal of a document failed: java.lang.IllegalStateException");
        assertOnlyDebugCarriesTheMarker();
    }

    @Test
    void aFailingRun() throws Exception {
        mvc.perform(post("/sources/broken/ingest")).andExpect(status().isInternalServerError());

        assertThat(messages(Level.ERROR)).containsExactly(
                "Ingestion run failed: RAG_E006 java.lang.IllegalStateException",
                "RAG_E006: dev.agentworkshq.rag.core.exception.ServiceException");
        assertOnlyDebugCarriesTheMarker();
    }

    @Test
    void aFailingAsk() throws Exception {
        doThrow(new IllegalStateException(MARKER)).when(store).similaritySearch(any(SearchRequest.class));

        mvc.perform(post("/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + MARKER + "\"}"))
                .andExpect(status().isInternalServerError());

        assertThat(messages(Level.ERROR)).containsExactly("RAG_E007: java.lang.IllegalStateException");
        assertOnlyDebugCarriesTheMarker();
    }

    @Test
    void aFailingUpload() throws Exception {
        doThrow(new IllegalStateException(MARKER)).when(store).add(anyList());

        mvc.perform(multipart("/documents")
                        .file(new MockMultipartFile("file", MARKER + ".txt", "text/plain", "content".getBytes())))
                .andExpect(status().isInternalServerError());

        assertThat(messages(Level.ERROR)).containsExactly("RAG_E007: java.lang.IllegalStateException");
        assertOnlyDebugCarriesTheMarker();
    }

    @Test
    void clientErrors() throws Exception {
        mvc.perform(get("/documents/" + MARKER + ".txt")).andExpect(status().isNotFound());
        mvc.perform(multipart("/documents")
                        .file(new MockMultipartFile("file", MARKER + ".exe", "text/plain", "content".getBytes())))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"" + MARKER + "\",\"topK\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/ask").contentType(MediaType.APPLICATION_JSON).content("{" + MARKER))
                .andExpect(status().isBadRequest());

        assertThat(messages(Level.WARN)).filteredOn(m -> m.startsWith("RAG_")).hasSize(4);
        assertOnlyDebugCarriesTheMarker();
    }
}
