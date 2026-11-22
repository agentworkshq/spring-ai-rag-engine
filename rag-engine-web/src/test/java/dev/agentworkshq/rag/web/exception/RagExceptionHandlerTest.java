package dev.agentworkshq.rag.web.exception;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.agentworkshq.rag.web.controller.TestController;
import dev.agentworkshq.rag.web.other.OutsideController;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RagExceptionHandlerTest {

    private final MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new TestController(), new OutsideController())
            .setControllerAdvice(new RagExceptionHandler())
            .build();

    @Test
    void baseExceptionReturnsEnvelopeWithEnumStatus() throws Exception {
        mvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("error"))
                .andExpect(jsonPath("$.errorCode").value("RAG_E002"))
                .andExpect(jsonPath("$.message").value("Document with id 42 not found"))
                .andExpect(jsonPath("$.path").value("/test/not-found"))
                .andExpect(jsonPath("$.timestamp").value(matchesPattern("\\d{4}-\\d{2}-\\d{2}T.*Z")));
    }

    @Test
    void invalidBodyReturnsFieldErrors() throws Exception {
        mvc.perform(post("/test/valid").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"email\":\"nope\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("RAG_E005"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists());
    }

    @Test
    void unsupportedMethodReturns405() throws Exception {
        mvc.perform(post("/test/ok"))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void unreadableBodyDoesNotLeakParserDetails() throws Exception {
        mvc.perform(post("/test/valid").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("RAG_E008"))
                .andExpect(content().string(not(containsString("Unrecognized"))))
                .andExpect(content().string(not(containsString("JsonParse"))));
    }

    @Test
    void catchAllReturnsGenericMessage() throws Exception {
        mvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.errorCode").value("RAG_E007"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andExpect(content().string(not(containsString("secret internal detail"))));
    }

    @Test
    void successOmitsNullOptionalFieldsButKeepsData() throws Exception {
        mvc.perform(get("/test/ok"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"data\":null")))
                .andExpect(content().string(not(containsString("errorCode"))))
                .andExpect(content().string(not(containsString("metadata"))))
                .andExpect(content().string(not(containsString("errors"))))
                .andExpect(content().string(not(containsString("path"))));
    }

    @Test
    void handlerIgnoresControllersOutsideItsPackage() {
        assertThatThrownBy(() -> mvc.perform(get("/outside/not-found")))
                .hasRootCauseInstanceOf(dev.agentworkshq.rag.core.exception.ResourceNotFoundException.class);
    }
}
