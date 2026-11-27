package dev.agentworkshq.rag.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import dev.agentworkshq.rag.core.answer.RagAnswer;
import dev.agentworkshq.rag.core.answer.RagService;
import dev.agentworkshq.rag.core.ingest.IngestionService;
import dev.agentworkshq.rag.core.store.IngestionJobStore;
import dev.agentworkshq.rag.web.controller.AskController;
import dev.agentworkshq.rag.web.controller.DocumentController;
import dev.agentworkshq.rag.web.controller.SourceController;
import dev.agentworkshq.rag.web.exception.RagExceptionHandler;
import dev.agentworkshq.rag.web.security.RequestGuard;
import dev.agentworkshq.rag.web.tenant.TenantContext;
import dev.agentworkshq.rag.web.tenant.TenantResolver;
import dev.agentworkshq.rag.web.upload.UploadService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class TenantFailClosedTest {

    private static final List<String> INVALID =
            Arrays.asList(null, "", "   ", "SECRET\ntenant", "SECRET\u001Btenant", "x".repeat(101));

    private final RagService ragService = mock(RagService.class);
    private final IngestionService ingestionService = mock(IngestionService.class);
    private final IngestionJobStore jobStore = mock(IngestionJobStore.class);
    private final UploadService uploads = mock(UploadService.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger logger = (Logger) LoggerFactory.getLogger(TenantContext.class);

    private String tenant;
    private final TenantResolver resolver = new FixedResolver();
    private MockMvc mvc;

    private class FixedResolver implements TenantResolver {

        @Override
        public String resolve(HttpServletRequest request) {
            return tenant;
        }
    }

    @BeforeEach
    void setUp() {
        RagWebProperties properties = new RagWebProperties(true, "/api/rag", false, "RAG_ADMIN",
                new RagWebProperties.Tenant(RagWebProperties.Tenant.Mode.CUSTOM, "default"),
                new RagWebProperties.Upload(true, true, 5));
        RequestGuard guard = new RequestGuard(properties);
        mvc = MockMvcBuilders.standaloneSetup(
                        new AskController(ragService, resolver, guard),
                        new SourceController(ingestionService, jobStore, resolver, guard),
                        new DocumentController(uploads, resolver, guard))
                .setControllerAdvice(new RagExceptionHandler())
                .build();
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void detach() {
        logger.detachAppender(logs);
    }

    private void assertFailsClosed(RequestBuilder request) throws Exception {
        for (String invalid : INVALID) {
            tenant = invalid;
            logs.list.clear();

            mvc.perform(request)
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("RAG_E024"))
                    .andExpect(jsonPath("$.message").value("Access denied"));

            assertThat(logs.list).singleElement().satisfies(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.WARN);
                assertThat(event.getFormattedMessage())
                        .isEqualTo("Invalid tenant id from " + FixedResolver.class.getName());
                assertThat(event.getThrowableProxy()).isNull();
            });
        }
        verifyNoInteractions(ragService, ingestionService, jobStore, uploads);
    }

    @Test
    void ask() throws Exception {
        assertFailsClosed(post("/ask").contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"q\"}"));
    }

    @Test
    void ingest() throws Exception {
        assertFailsClosed(post("/sources/docs/ingest"));
    }

    @Test
    void upload() throws Exception {
        assertFailsClosed(multipart("/documents")
                .file(new MockMultipartFile("file", "a.txt", "text/plain", "content".getBytes())));
    }

    @Test
    void list() throws Exception {
        assertFailsClosed(get("/documents"));
    }

    @Test
    void getOne() throws Exception {
        assertFailsClosed(get("/documents/a.txt"));
    }

    @Test
    void deleteOne() throws Exception {
        assertFailsClosed(delete("/documents/a.txt"));
    }

    @Test
    void askRejectsInvalidSourceNames() throws Exception {
        tenant = "t1";
        for (String name : new String[] {"x".repeat(101), "a\\nb", "a\\u001bb", "  "}) {
            mvc.perform(post("/ask").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\":\"q\",\"sourceNames\":[\"" + name + "\"]}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("RAG_E005"));
        }
        mvc.perform(post("/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"q\",\"sourceNames\":[null]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(ragService);
    }

    @Test
    void askAcceptsASourceNameOfTheMaximumLength() throws Exception {
        tenant = "x".repeat(100);
        when(ragService.ask(any(), any())).thenReturn(new RagAnswer("ok", List.of(), true));

        mvc.perform(post("/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"q\",\"sourceNames\":[\"" + "s".repeat(100) + "\"]}"))
                .andExpect(status().isOk());
    }
}
