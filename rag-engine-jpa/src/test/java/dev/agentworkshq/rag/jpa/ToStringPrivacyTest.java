package dev.agentworkshq.rag.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import dev.agentworkshq.rag.jpa.entity.IngestionJob;
import dev.agentworkshq.rag.jpa.entity.SourceDocument;
import org.junit.jupiter.api.Test;

class ToStringPrivacyTest {

    private static final String MARKER = "SECRET-MARKER-TEXT";

    @Test
    void ingestionJob() {
        IngestionJob job = new IngestionJob();
        job.setSourceName(MARKER);
        job.setErrorMessage(MARKER);

        assertThat(job.toString()).doesNotContain(MARKER);
    }

    @Test
    void sourceDocument() {
        SourceDocument document = new SourceDocument();
        document.setSourceName(MARKER);
        document.setExternalId(MARKER);
        document.setTenantId(MARKER);
        document.setContentHash(MARKER);

        assertThat(document.toString()).doesNotContain(MARKER);
    }
}
