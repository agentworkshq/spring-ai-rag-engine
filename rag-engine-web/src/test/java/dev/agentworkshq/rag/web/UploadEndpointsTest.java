package dev.agentworkshq.rag.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.agentworkshq.rag.core.answer.RagService;
import dev.agentworkshq.rag.core.ingest.DefaultDocumentLoader;
import dev.agentworkshq.rag.core.ingest.IngestionLimits;
import dev.agentworkshq.rag.core.ingest.IngestionPipeline;
import dev.agentworkshq.rag.core.ingest.IngestionService;
import dev.agentworkshq.rag.core.ingest.RecursiveChunker;
import dev.agentworkshq.rag.core.ingest.SourceRegistry;
import dev.agentworkshq.rag.core.store.InMemoryDocumentRegistry;
import dev.agentworkshq.rag.core.store.InMemoryIngestionJobStore;
import dev.agentworkshq.rag.core.store.IngestionJobStore;
import dev.agentworkshq.rag.web.controller.DocumentController;
import dev.agentworkshq.rag.web.controller.TooLargeController;
import dev.agentworkshq.rag.web.tenant.TenantResolver;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class UploadEndpointsTest {

    private static final String DOCS = "/api/rag/documents";
    private static final String[] OPEN = {
            "rag-engine.web.require-authentication=false", "rag-engine.web.upload.enabled=true"};
    private static final String TEMP_PREFIX = "rag-upload-";

    private static List<String> tempFilesBefore;

    @BeforeAll
    static void rememberTempFiles() throws IOException {
        tempFilesBefore = uploadTempFiles();
    }

    @AfterAll
    static void noTempFilesLeftBehind() throws IOException {
        assertThat(uploadTempFiles()).isEqualTo(tempFilesBefore);
    }

    private static List<String> uploadTempFiles() throws IOException {
        try (Stream<Path> files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return files.map(p -> p.getFileName().toString()).filter(n -> n.startsWith(TEMP_PREFIX)).sorted()
                    .toList();
        }
    }

    @Configuration
    @EnableWebMvc
    static class Beans {

        @Bean
        RagService ragService() {
            return mock(RagService.class);
        }

        @Bean
        InMemoryDocumentRegistry documentRegistry() {
            return new InMemoryDocumentRegistry();
        }

        @Bean
        IngestionJobStore jobStore() {
            return new InMemoryIngestionJobStore();
        }

        @Bean
        VectorStore vectorStore() {
            return SimpleVectorStore.builder(new TinyEmbeddingModel()).build();
        }

        @Bean
        IngestionLimits limits() {
            return new IngestionLimits(1000, 10);
        }

        @Bean
        IngestionPipeline pipeline(VectorStore store, InMemoryDocumentRegistry registry, IngestionJobStore jobs,
                                   IngestionLimits limits) {
            return new IngestionPipeline(() -> store, new DefaultDocumentLoader(limits.maxFileSize(), 10),
                    new RecursiveChunker(100, 10), registry, jobs, "RECURSIVE:100:10", 50, false);
        }

        @Bean
        IngestionService ingestionService(IngestionPipeline pipeline) {
            return new IngestionService(new SourceRegistry(List.of()), pipeline);
        }

        @Bean
        TooLargeController tooLargeController() {
            return new TooLargeController();
        }
    }


    static class TinyEmbeddingModel implements EmbeddingModel {

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> result = new ArrayList<>();
            for (int i = 0; i < request.getInstructions().size(); i++) {
                result.add(new Embedding(vector(request.getInstructions().get(i)), i));
            }
            return new EmbeddingResponse(result);
        }

        @Override
        public float[] embed(Document document) {
            return vector(document.getText());
        }

        @Override
        public int dimensions() {
            return 4;
        }

        private static float[] vector(String text) {
            return new float[] {1, text.length(), 2, 3};
        }
    }

    @FunctionalInterface
    interface Check {
        void run(AssertableWebApplicationContext context, MockMvc mvc) throws Exception;
    }

    private WebApplicationContextRunner runner() {
        return new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RagWebAutoConfiguration.class))
                .withUserConfiguration(Beans.class)
                .withPropertyValues("rag-engine.web.enabled=true");
    }

    private void run(String[] properties, Check check) {
        runner().withPropertyValues(properties).run(context -> {
            assertThat(context).hasNotFailed();
            check.run(context, mockMvc(context));
        });
    }

    private static MockMvc mockMvc(AssertableWebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup((WebApplicationContext) context.getSourceApplicationContext())
                .build();
    }

    private static String[] props(String... extra) {
        List<String> all = new ArrayList<>(List.of(OPEN));
        all.addAll(List.of(extra));
        return all.toArray(String[]::new);
    }

    private static MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("file", name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }

    private static RequestPostProcessor user(String... roles) {
        return request -> {
            request.setUserPrincipal(() -> "alice");
            for (String role : roles) {
                request.addUserRole(role);
            }
            return request;
        };
    }

    @Test
    void uploadAddsThenSkipsThenUpdates() {
        run(OPEN, (context, mvc) -> {
            mvc.perform(multipart(DOCS).file(file("a.txt", "Alpha content. ".repeat(10))))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", DOCS + "/a.txt"))
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.data.externalId").value("a.txt"))
                    .andExpect(jsonPath("$.data.filename").value("a.txt"))
                    .andExpect(jsonPath("$.data.outcome").value("ADDED"))
                    .andExpect(jsonPath("$.data.chunkCount").isNumber());
            mvc.perform(multipart(DOCS).file(file("a.txt", "Alpha content. ".repeat(10))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.outcome").value("SKIPPED"));
            mvc.perform(multipart(DOCS).file(file("a.txt", "Omega content. ".repeat(10))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.outcome").value("UPDATED"));
        });
    }

    @Test
    void pathSegmentsAreStrippedFromFileNames() {
        run(OPEN, (context, mvc) -> {
            mvc.perform(multipart(DOCS).file(file("../../etc/passwd.txt", "root")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.externalId").value("passwd.txt"));
            mvc.perform(multipart(DOCS).file(file("..\\..\\win\\system.txt", "root")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.externalId").value("system.txt"));
        });
    }

    @Test
    void invalidFileNamesAreRejected() {
        run(OPEN, (context, mvc) -> {
            for (String name : new String[] {"..", ".hidden.txt", "a..b.txt", "x".repeat(197) + ".txt", "",
                    "dir/", "bad\u0001name.txt"}) {
                mvc.perform(multipart(DOCS).file(file(name, "content")))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.errorCode").value("RAG_E027"));
            }
            mvc.perform(multipart(DOCS).file(file("x".repeat(196) + ".txt", "content")))
                    .andExpect(status().isCreated());
        });
    }

    @Test
    void unsupportedExtensionAndFakePdfAreRejected() {
        run(OPEN, (context, mvc) -> {
            mvc.perform(multipart(DOCS).file(file("tool.exe", "content")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("RAG_E012"));
            mvc.perform(multipart(DOCS).file(file("noext", "content")))
                    .andExpect(status().isBadRequest());
            mvc.perform(multipart(DOCS).file(file("fake.pdf", "this is not a pdf")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("RAG_E028"));
            mvc.perform(multipart(DOCS).file(file("tiny.pdf", "%P")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("RAG_E028"));
        });
    }

    @Test
    void oversizeUploadGives413() {
        run(OPEN, (context, mvc) -> {
            mvc.perform(multipart(DOCS).file(file("big.txt", "x".repeat(2000))))
                    .andExpect(status().isPayloadTooLarge())
                    .andExpect(jsonPath("$.errorCode").value("RAG_E030"));
            mvc.perform(get(DOCS)).andExpect(jsonPath("$.data.length()").value(0));
        });
    }

    @Test
    void understatedSizeIsStillCappedWhileStreaming() {
        run(OPEN, (context, mvc) -> {
            MockMultipartFile lying = new MockMultipartFile("file", "big.txt", "text/plain",
                    "x".repeat(2000).getBytes(StandardCharsets.UTF_8)) {
                @Override
                public long getSize() {
                    return 1;
                }

                @Override
                public InputStream getInputStream() {
                    return new java.io.ByteArrayInputStream("x".repeat(2000).getBytes(StandardCharsets.UTF_8));
                }
            };

            mvc.perform(multipart(DOCS).file(lying))
                    .andExpect(status().isPayloadTooLarge())
                    .andExpect(jsonPath("$.errorCode").value("RAG_E030"));
            assertThat(uploadTempFiles()).isEqualTo(tempFilesBefore);
        });
    }

    @Test
    void containerSizeLimitMapsTo413() {
        run(OPEN, (context, mvc) ->
                mvc.perform(post("/api/rag/too-large"))
                        .andExpect(status().isPayloadTooLarge())
                        .andExpect(jsonPath("$.errorCode").value("RAG_E025")));
    }

    @Test
    void quotaBlocksNewFilesButNotReplacements() {
        run(props("rag-engine.web.upload.max-documents-per-tenant=2"), (context, mvc) -> {
            mvc.perform(multipart(DOCS).file(file("a.txt", "one"))).andExpect(status().isCreated());
            mvc.perform(multipart(DOCS).file(file("b.txt", "two"))).andExpect(status().isCreated());
            mvc.perform(multipart(DOCS).file(file("c.txt", "three")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("RAG_E029"));
            mvc.perform(multipart(DOCS).file(file("a.txt", "one changed")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.outcome").value("UPDATED"));
        });
    }

    @Test
    void deleteReturns204ThenNotFound() {
        run(OPEN, (context, mvc) -> {
            mvc.perform(multipart(DOCS).file(file("a.txt", "content"))).andExpect(status().isCreated());

            mvc.perform(delete(DOCS + "/a.txt")).andExpect(status().isNoContent());
            mvc.perform(delete(DOCS + "/a.txt")).andExpect(status().isNotFound());
            mvc.perform(delete(DOCS + "/.hidden")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("RAG_E027"));
            mvc.perform(delete(DOCS + "/a..b.txt")).andExpect(status().isBadRequest());
        });
    }

    @Test
    void getReturnsOneDocumentOr404() {
        run(OPEN, (context, mvc) -> {
            mvc.perform(get(DOCS + "/a.txt")).andExpect(status().isNotFound());
            mvc.perform(multipart(DOCS).file(file("a.txt", "Alpha content. ".repeat(10))))
                    .andExpect(status().isCreated());
            mvc.perform(get(DOCS + "/a.txt"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.data.externalId").value("a.txt"))
                    .andExpect(jsonPath("$.data.chunkCount").isNumber());
            mvc.perform(get(DOCS + "/.hidden")).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("RAG_E027"));
        });
    }

    @Test
    void listIsPagedWithMetadata() {
        run(OPEN, (context, mvc) -> {
            for (String name : new String[] {"a.txt", "b.txt", "c.txt"}) {
                mvc.perform(multipart(DOCS).file(file(name, "content " + name))).andExpect(status().isCreated());
            }

            mvc.perform(get(DOCS + "?page=0&size=2"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].externalId").value("a.txt"))
                    .andExpect(jsonPath("$.metadata.currentPage").value(0))
                    .andExpect(jsonPath("$.metadata.pageSize").value(2))
                    .andExpect(jsonPath("$.metadata.totalPages").value(2))
                    .andExpect(jsonPath("$.metadata.totalItems").value(3));
            mvc.perform(get(DOCS + "?page=1&size=2"))
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].externalId").value("c.txt"));
            mvc.perform(get(DOCS + "?page=5&size=2")).andExpect(jsonPath("$.data.length()").value(0));
            mvc.perform(get(DOCS)).andExpect(jsonPath("$.metadata.pageSize").value(20));
            mvc.perform(get(DOCS + "?size=101")).andExpect(status().isBadRequest());
            mvc.perform(get(DOCS + "?size=0")).andExpect(status().isBadRequest());
            mvc.perform(get(DOCS + "?page=-1")).andExpect(status().isBadRequest());
        });
    }

    @Test
    void tenantsOnlySeeAndDeleteTheirOwnDocuments() {
        TenantResolver byHeader = request -> request.getHeader("X-Test-Tenant");
        runner().withPropertyValues(props("rag-engine.web.tenant.mode=CUSTOM"))
                .withBean(TenantResolver.class, () -> byHeader)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    MockMvc mvc = mockMvc(context);

                    mvc.perform(multipart(DOCS).file(file("a.txt", "tenant a")).header("X-Test-Tenant", "A"))
                            .andExpect(status().isCreated());

                    mvc.perform(get(DOCS).header("X-Test-Tenant", "B"))
                            .andExpect(jsonPath("$.data.length()").value(0));
                    mvc.perform(get(DOCS + "/a.txt").header("X-Test-Tenant", "B"))
                            .andExpect(status().isNotFound());
                    mvc.perform(get(DOCS + "/a.txt").header("X-Test-Tenant", "A"))
                            .andExpect(status().isOk());
                    mvc.perform(delete(DOCS + "/a.txt").header("X-Test-Tenant", "B"))
                            .andExpect(status().isNotFound());
                    mvc.perform(get(DOCS).header("X-Test-Tenant", "A"))
                            .andExpect(jsonPath("$.data.length()").value(1));
                    mvc.perform(multipart(DOCS).file(file("a.txt", "tenant a")).header("X-Test-Tenant", "B"))
                            .andExpect(status().isCreated())
                            .andExpect(jsonPath("$.data.outcome").value("ADDED"));
                    mvc.perform(delete(DOCS + "/a.txt").header("X-Test-Tenant", "A"))
                            .andExpect(status().isNoContent());
                    mvc.perform(get(DOCS).header("X-Test-Tenant", "B"))
                            .andExpect(jsonPath("$.data.length()").value(1));
                });
    }

    @Test
    void endpointsDoNotExistWhenUploadIsDisabled() {
        run(new String[] {"rag-engine.web.require-authentication=false"}, (context, mvc) -> {
            assertThat(context).doesNotHaveBean(DocumentController.class);
            mvc.perform(multipart(DOCS).file(file("a.txt", "content"))).andExpect(status().isNotFound());
            mvc.perform(get(DOCS)).andExpect(status().isNotFound());
            mvc.perform(delete(DOCS + "/a.txt")).andExpect(status().isNotFound());
        });
    }

    @Test
    void adminRoleIsRequiredByDefault() {
        run(new String[] {"rag-engine.web.upload.enabled=true"}, (context, mvc) -> {
            mvc.perform(multipart(DOCS).file(file("a.txt", "content"))).andExpect(status().isUnauthorized());
            mvc.perform(multipart(DOCS).file(file("a.txt", "content")).with(user("OTHER")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("RAG_E024"));
            mvc.perform(get(DOCS).with(user("OTHER"))).andExpect(status().isForbidden());
            mvc.perform(get(DOCS + "/a.txt").with(user("OTHER"))).andExpect(status().isForbidden());
            mvc.perform(delete(DOCS + "/a.txt").with(user("OTHER"))).andExpect(status().isForbidden());
            mvc.perform(multipart(DOCS).file(file("a.txt", "content")).with(user("RAG_ADMIN")))
                    .andExpect(status().isCreated());
        });
    }

    @Test
    void requireAdminFalseOnlyNeedsAuthentication() {
        run(new String[] {"rag-engine.web.upload.enabled=true", "rag-engine.web.upload.require-admin=false"},
                (context, mvc) -> {
                    mvc.perform(multipart(DOCS).file(file("a.txt", "content"))).andExpect(status().isUnauthorized());
                    mvc.perform(multipart(DOCS).file(file("a.txt", "content")).with(user("OTHER")))
                            .andExpect(status().isCreated());
                });
    }

    @Test
    void missingFilePartGives400() {
        run(OPEN, (context, mvc) ->
                mvc.perform(multipart(DOCS))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.errorCode").value("RAG_E026")));
    }

    @Test
    void invalidQuotaPropertyFailsStartup() {
        runner().withPropertyValues("rag-engine.web.upload.max-documents-per-tenant=0")
                .run(context -> assertThat(context).hasFailed());
    }
}
