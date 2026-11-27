package dev.agentworkshq.rag.web.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.ResourceNotFoundException;
import dev.agentworkshq.rag.core.exception.ServiceException;
import dev.agentworkshq.rag.web.exception.RagExceptionHandler;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class ErrorResponseTest {

    private static final String PATH = "/srv/private/docs";

    @RestController
    static class FailingController implements RagControllers {

        @GetMapping("/server/{code}")
        void server(@PathVariable ErrorMessages code) {
            throw new ServiceException(new IllegalStateException(PATH), code, PATH, "private-argument");
        }

        @GetMapping({"/client", "/client/{segment}"})
        void client(@RequestParam String name) {
            throw new ResourceNotFoundException(ErrorMessages.RAG_E002, name);
        }
    }

    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new FailingController())
            .setControllerAdvice(new RagExceptionHandler())
            .build();

    @Test
    void noServerErrorContainsAPathOrAnArgument() throws Exception {
        for (ErrorMessages code : Arrays.stream(ErrorMessages.values())
                .filter(e -> e.getHttpStatus() >= 500).toList()) {
            mvc.perform(get("/server/" + code.name()))
                    .andExpect(status().is(code.getHttpStatus()))
                    .andExpect(jsonPath("$.errorCode").value(code.name()))
                    .andExpect(jsonPath("$.message").value(code.getPublicMessage()))
                    .andExpect(content().string(not(containsString("private"))))
                    .andExpect(content().string(not(containsString("%s"))));
        }
    }

    @Test
    void aClientErrorCleansAnEchoedName() throws Exception {
        mvc.perform(get("/client").param("name", "a\nb\u001B[31mred\u202E.txt"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Document with id ab[31mred.txt not found"));
    }

    @Test
    void aClientErrorCutsAVeryLongName() throws Exception {
        String name = "n".repeat(5000);

        String body = mvc.perform(get("/client/" + name).param("name", name))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Document with id " + "n".repeat(64) + " not found"))
                .andExpect(jsonPath("$.path").value("/client/" + "n".repeat(64)))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("n".repeat(65));
    }
}
