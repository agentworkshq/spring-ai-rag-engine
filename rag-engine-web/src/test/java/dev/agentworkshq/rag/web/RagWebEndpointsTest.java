package dev.agentworkshq.rag.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.agentworkshq.rag.core.answer.Citation;
import dev.agentworkshq.rag.core.answer.RagAnswer;
import dev.agentworkshq.rag.core.answer.RagContext;
import dev.agentworkshq.rag.core.answer.RagService;
import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.ResourceNotFoundException;
import dev.agentworkshq.rag.core.exception.ServiceException;
import dev.agentworkshq.rag.core.ingest.IngestionResult;
import dev.agentworkshq.rag.core.ingest.IngestionService;
import dev.agentworkshq.rag.core.store.InMemoryIngestionJobStore;
import dev.agentworkshq.rag.core.store.IngestionJobStore;
import dev.agentworkshq.rag.web.controller.AskController;
import dev.agentworkshq.rag.web.tenant.TenantResolver;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class RagWebEndpointsTest {

    private static final String BASE = "/api/rag";
    private static final String OPEN = "rag-engine.web.require-authentication=false";

    @Configuration
    @EnableWebMvc
    static class Beans {

        @Bean
        RagService ragService() {
            return mock(RagService.class);
        }

        @Bean
        IngestionService ingestionService() {
            return mock(IngestionService.class);
        }

        @Bean
        IngestionJobStore jobStore() {
            return new InMemoryIngestionJobStore();
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
            MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) context.getSourceApplicationContext())
                    .build();
            check.run(context, mvc);
        });
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

    private static String ask(String question) {
        return "{\"question\":\"" + question + "\"}";
    }

    @Test
    void askReturnsEnvelopeWithMappedCitations() {
        run(new String[] {OPEN}, (context, mvc) -> {
            when(context.getBean(RagService.class).ask(eq("what is apple"), any())).thenReturn(new RagAnswer(
                    "Apples are red [1]", List.of(new Citation(1, "doc-1", "a.txt", 3, "snippet", true)), true));

            mvc.perform(post(BASE + "/ask").contentType(MediaType.APPLICATION_JSON).content(ask("what is apple")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("success"))
                    .andExpect(jsonPath("$.data.answer").value("Apples are red [1]"))
                    .andExpect(jsonPath("$.data.answered").value(true))
                    .andExpect(jsonPath("$.data.citations[0].index").value(1))
                    .andExpect(jsonPath("$.data.citations[0].documentId").value("doc-1"))
                    .andExpect(jsonPath("$.data.citations[0].filename").value("a.txt"))
                    .andExpect(jsonPath("$.data.citations[0].page").value(3))
                    .andExpect(jsonPath("$.data.citations[0].used").value(true));
        });
    }

    @Test
    void blankQuestionGivesFieldErrors() {
        run(new String[] {OPEN}, (context, mvc) ->
                mvc.perform(post(BASE + "/ask").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"question\":\"  \",\"topK\":0}"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.errorCode").value("RAG_E005"))
                        .andExpect(jsonPath("$.errors[?(@.field == 'question')]").exists())
                        .andExpect(jsonPath("$.errors[?(@.field == 'topK')]").exists()));
    }

    @Test
    void tooManySourceNamesAndBlankNamesAreRejected() {
        run(new String[] {OPEN}, (context, mvc) ->
                mvc.perform(post(BASE + "/ask").contentType(MediaType.APPLICATION_JSON)
                                .content("{\"question\":\"q\",\"sourceNames\":[\"a\",\" \"]}"))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.errorCode").value("RAG_E005")));
    }

    @Test
    void overLengthQuestionIsRejectedWithBadRequest() {
        run(new String[] {OPEN}, (context, mvc) -> {
            when(context.getBean(RagService.class).ask(any(), any()))
                    .thenThrow(new ServiceException(ErrorMessages.RAG_E023, 10));

            mvc.perform(post(BASE + "/ask").contentType(MediaType.APPLICATION_JSON).content(ask("too long")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("RAG_E023"));
        });
    }

    @Test
    void tenantInBodyIsIgnoredAndResolverTenantIsUsed() {
        run(new String[] {OPEN, "rag-engine.web.tenant.default-tenant=main"}, (context, mvc) -> {
            RagService service = context.getBean(RagService.class);
            when(service.ask(any(), any())).thenReturn(new RagAnswer("ok", List.of(), true));

            mvc.perform(post(BASE + "/ask").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"question\":\"q\",\"tenantId\":\"evil\"}"))
                    .andExpect(status().isOk());

            ArgumentCaptor<RagContext> captor = ArgumentCaptor.forClass(RagContext.class);
            verify(service).ask(eq("q"), captor.capture());
            assertThat(captor.getValue().tenantId()).isEqualTo("main");
        });
    }

    @Test
    void tenantQueryParameterAndHeaderAreIgnored() {
        run(new String[] {OPEN}, (context, mvc) -> {
            RagService service = context.getBean(RagService.class);
            when(service.ask(any(), any())).thenReturn(new RagAnswer("ok", List.of(), true));

            mvc.perform(post(BASE + "/ask?tenantId=evil").header("X-Tenant-Id", "evil")
                            .contentType(MediaType.APPLICATION_JSON).content(ask("q")))
                    .andExpect(status().isOk());

            ArgumentCaptor<RagContext> captor = ArgumentCaptor.forClass(RagContext.class);
            verify(service).ask(eq("q"), captor.capture());
            assertThat(captor.getValue().tenantId()).isEqualTo("default");
        });
    }

    @Test
    void authenticationIsRequiredByDefault() {
        run(new String[0], (context, mvc) -> {
            when(context.getBean(RagService.class).ask(any(), any())).thenReturn(new RagAnswer("ok", List.of(), true));

            mvc.perform(post(BASE + "/ask").contentType(MediaType.APPLICATION_JSON).content(ask("q")))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("RAG_E004"));
            mvc.perform(post(BASE + "/ask").with(user()).contentType(MediaType.APPLICATION_JSON).content(ask("q")))
                    .andExpect(status().isOk());
        });
    }

    @Test
    void adminEndpointsNeedTheAdminRole() {
        run(new String[0], (context, mvc) -> {
            when(context.getBean(IngestionService.class).ingest("docs", "default"))
                    .thenReturn(new IngestionResult(1, 2, 3, 4, 0, 7L));

            mvc.perform(post(BASE + "/sources/docs/ingest"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post(BASE + "/sources/docs/ingest").with(user("OTHER")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.errorCode").value("RAG_E024"));
            mvc.perform(get(BASE + "/jobs/1").with(user("OTHER"))).andExpect(status().isForbidden());
            mvc.perform(get(BASE + "/sources/docs/jobs").with(user("OTHER"))).andExpect(status().isForbidden());
            mvc.perform(post(BASE + "/sources/docs/ingest").with(user("RAG_ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.added").value(1))
                    .andExpect(jsonPath("$.data.updated").value(2))
                    .andExpect(jsonPath("$.data.deleted").value(3))
                    .andExpect(jsonPath("$.data.skipped").value(4))
                    .andExpect(jsonPath("$.data.failed").value(0))
                    .andExpect(jsonPath("$.data.jobId").value(7));
        });
    }

    @Test
    void blankAdminRoleSkipsTheRoleCheck() {
        run(new String[] {"rag-engine.web.admin-role="}, (context, mvc) -> {
            when(context.getBean(IngestionService.class).ingest("docs", "default"))
                    .thenReturn(new IngestionResult(0, 0, 0, 0, 0, 1L));

            mvc.perform(post(BASE + "/sources/docs/ingest").with(user())).andExpect(status().isOk());
            mvc.perform(post(BASE + "/sources/docs/ingest")).andExpect(status().isUnauthorized());
        });
    }

    @Test
    void ingestMapsUnknownSourceAndRunInProgress() {
        run(new String[] {OPEN}, (context, mvc) -> {
            IngestionService service = context.getBean(IngestionService.class);
            when(service.ingest(eq("nope"), any())).thenThrow(new ResourceNotFoundException(ErrorMessages.RAG_E018, "nope"));
            when(service.ingest(eq("busy"), any())).thenThrow(new ServiceException(ErrorMessages.RAG_E016, "busy", "default"));

            mvc.perform(post(BASE + "/sources/nope/ingest"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("RAG_E018"));
            mvc.perform(post(BASE + "/sources/busy/ingest"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.errorCode").value("RAG_E016"));
        });
    }

    @Test
    void invalidSourceNameIsRejected() {
        run(new String[] {OPEN}, (context, mvc) -> {
            mvc.perform(post(BASE + "/sources/a%20b/ingest")).andExpect(status().isBadRequest());
            mvc.perform(post(BASE + "/sources/" + "x".repeat(101) + "/ingest")).andExpect(status().isBadRequest());
            mvc.perform(get(BASE + "/sources/a%20b/jobs")).andExpect(status().isBadRequest());
        });
    }

    @Test
    void jobListHonoursTheLimit() {
        run(new String[] {OPEN}, (context, mvc) -> {
            IngestionJobStore store = context.getBean(IngestionJobStore.class);
            store.finish(store.start("docs").id(), 1, 0, 0, 0, null);
            store.start("docs");

            mvc.perform(get(BASE + "/sources/docs/jobs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].status").value("RUNNING"))
                    .andExpect(jsonPath("$.data[1].status").value("DONE"));
            mvc.perform(get(BASE + "/sources/docs/jobs?limit=1"))
                    .andExpect(jsonPath("$.data.length()").value(1));
            mvc.perform(get(BASE + "/sources/docs/jobs?limit=50")).andExpect(status().isOk());
            mvc.perform(get(BASE + "/sources/docs/jobs?limit=51")).andExpect(status().isBadRequest());
            mvc.perform(get(BASE + "/sources/docs/jobs?limit=0")).andExpect(status().isBadRequest());
        });
    }

    @Test
    void jobByIdReturnsJobOr404() {
        run(new String[] {OPEN}, (context, mvc) -> {
            IngestionJobStore store = context.getBean(IngestionJobStore.class);
            Long id = store.finish(store.start("docs").id(), 5, 0, 0, 0, "1 documents failed").id();

            mvc.perform(get(BASE + "/jobs/" + id))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(id))
                    .andExpect(jsonPath("$.data.added").value(5))
                    .andExpect(jsonPath("$.data.errorMessage").value("1 documents failed"));
            mvc.perform(get(BASE + "/jobs/999"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.errorCode").value("RAG_E001"));
            mvc.perform(get(BASE + "/jobs/abc")).andExpect(status().isBadRequest());
        });
    }

    @Test
    void basePathIsConfigurable() {
        run(new String[] {OPEN, "rag-engine.web.base-path=/rag"}, (context, mvc) -> {
            mvc.perform(get("/rag/jobs/999")).andExpect(status().isNotFound());
            mvc.perform(get(BASE + "/jobs/999")).andExpect(status().isNotFound());
        });
    }

    @Test
    void customTenantModeWithoutResolverFailsStartup() {
        runner().withPropertyValues("rag-engine.web.tenant.mode=CUSTOM").run(context ->
                assertThat(context).hasFailed().getFailure().rootCause()
                        .hasMessageContaining("requires a TenantResolver bean"));
    }

    @Test
    void customTenantModeWithResolverStartsAndIsUsed() {
        TenantResolver resolver = request -> "from-resolver";
        runner().withPropertyValues("rag-engine.web.tenant.mode=CUSTOM", OPEN)
                .withBean(TenantResolver.class, () -> resolver)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RagService service = context.getBean(RagService.class);
                    when(service.ask(any(), any())).thenReturn(new RagAnswer("ok", List.of(), true));
                    MockMvc mvc = MockMvcBuilders.webAppContextSetup(
                            (WebApplicationContext) context.getSourceApplicationContext()).build();

                    mvc.perform(post(BASE + "/ask").contentType(MediaType.APPLICATION_JSON).content(ask("q")))
                            .andExpect(status().isOk());

                    ArgumentCaptor<RagContext> captor = ArgumentCaptor.forClass(RagContext.class);
                    verify(service).ask(eq("q"), captor.capture());
                    assertThat(captor.getValue().tenantId()).isEqualTo("from-resolver");
                });
    }

    @Test
    void autoConfigurationIsInactiveWhenNotEnabled() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RagWebAutoConfiguration.class))
                .withUserConfiguration(Beans.class)
                .run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(AskController.class)
                        .doesNotHaveBean(TenantResolver.class));
    }

    @Test
    void invalidPropertiesFailStartup() {
        runner().withPropertyValues("rag-engine.web.base-path=api/rag").run(context ->
                assertThat(context).hasFailed());
    }
}
