package dev.agentworkshq.rag.web;

import static org.assertj.core.api.Assertions.assertThat;

import dev.agentworkshq.rag.web.dto.ApiError;
import dev.agentworkshq.rag.web.dto.ApiResponse;
import dev.agentworkshq.rag.web.dto.AskRequest;
import dev.agentworkshq.rag.web.dto.AskResponse;
import dev.agentworkshq.rag.web.dto.CitationResponse;
import dev.agentworkshq.rag.web.dto.DocumentResponse;
import dev.agentworkshq.rag.web.dto.JobResponse;
import dev.agentworkshq.rag.web.tenant.SingleTenantResolver;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToStringPrivacyTest {

    private static final String MARKER = "SECRET-MARKER-TEXT";
    private static final Instant NOW = Instant.parse("2026-01-02T03:04:05Z");

    private static CitationResponse citation() {
        return new CitationResponse(1, MARKER, MARKER, 2, MARKER, true);
    }

    private static void assertHidden(Object value) {
        assertThat(value.toString()).startsWith(value.getClass().getSimpleName() + "[").doesNotContain(MARKER);
    }

    @Test
    void askRequest() {
        AskRequest request = new AskRequest(MARKER, List.of(MARKER), 3);

        assertHidden(request);
        assertThat(request.toString()).isEqualTo("AskRequest[questionLength=18, sourceNames=1, topK=3]");
        assertThat(new AskRequest(null, null, null).toString()).contains("questionLength=0", "sourceNames=0");
    }

    @Test
    void askResponse() {
        AskResponse response = new AskResponse(MARKER, true, List.of(citation()));

        assertHidden(response);
        assertThat(response.toString()).contains("answerLength=18", "citations=1");
    }

    @Test
    void citationResponse() {
        assertHidden(citation());
    }

    @Test
    void documentResponse() {
        assertHidden(new DocumentResponse(MARKER, MARKER, MARKER, 2));
        assertThat(new DocumentResponse(MARKER, MARKER, "ADDED", 2).toString())
                .isEqualTo("DocumentResponse[outcome=ADDED, chunkCount=2]");
    }

    @Test
    void jobResponse() {
        assertHidden(new JobResponse(1L, MARKER, MARKER, NOW, NOW, 1, 2, 3, 4, MARKER));
        assertThat(new JobResponse(1L, MARKER, "DONE", NOW, NOW, 1, 2, 3, 4, null).toString())
                .contains("id=1", "status=DONE", "errorMessageLength=0");
    }

    @Test
    void apiResponseHidesEveryTextAndItsData() {
        ApiResponse<String> response = new ApiResponse<>(MARKER, MARKER, MARKER, MARKER, MARKER,
                List.of(new ApiError(MARKER, MARKER)), MARKER, NOW);

        assertHidden(response);
        assertThat(response.toString()).contains("hasData=true", "errors=1");
        assertThat(ApiResponse.error("RAG_E005", MARKER, MARKER).toString())
                .contains("status=error", "errorCode=RAG_E005");
    }

    @Test
    void apiError() {
        assertHidden(new ApiError(MARKER, MARKER));
    }

    @Test
    void webProperties() {
        RagWebProperties.Tenant tenant = new RagWebProperties.Tenant(RagWebProperties.Tenant.Mode.SINGLE, MARKER);
        RagWebProperties.Upload upload = new RagWebProperties.Upload(true, true, 5);
        RagWebProperties properties = new RagWebProperties(true, "/" + MARKER, true, MARKER, tenant, upload);

        assertHidden(properties);
        assertHidden(tenant);
        assertHidden(upload);
    }

    @Test
    void singleTenantResolver() {
        assertHidden(new SingleTenantResolver(MARKER));
    }
}
