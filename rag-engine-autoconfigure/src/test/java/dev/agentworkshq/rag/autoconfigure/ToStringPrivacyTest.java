package dev.agentworkshq.rag.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class ToStringPrivacyTest {

    private static final String MARKER = "SECRET-MARKER-TEXT";

    @Test
    void propertiesNeverPrintAValue() {
        RagEngineProperties.Chunking chunking =
                new RagEngineProperties.Chunking(RagEngineProperties.Chunking.Strategy.RECURSIVE, 800, 100);
        RagEngineProperties.Limits limits = new RagEngineProperties.Limits(DataSize.ofMegabytes(1), 10);
        RagEngineProperties.Ingestion ingestion = new RagEngineProperties.Ingestion(50, false, 0.5);
        RagEngineProperties.Retrieval retrieval = new RagEngineProperties.Retrieval(6, 20, 0.65, false);
        RagEngineProperties.Answer answer = new RagEngineProperties.Answer(true, MARKER, 12000, 2000);
        RagEngineProperties.Source source = new RagEngineProperties.Source(MARKER,
                RagEngineProperties.Source.Type.FILESYSTEM, MARKER, List.of(MARKER));
        RagEngineProperties properties = new RagEngineProperties(true, chunking, limits, ingestion, retrieval,
                answer, List.of(source));

        assertThat(List.of(properties, chunking, limits, ingestion, retrieval, answer, source))
                .allSatisfy(value -> assertThat(value.toString())
                        .startsWith(value.getClass().getSimpleName() + "[")
                        .doesNotContain(MARKER));
        assertThat(properties.toString()).isEqualTo("RagEngineProperties[sources=1]");
        assertThat(source.toString()).isEqualTo("Source[include=1]");
        assertThat(answer.toString()).isEqualTo("Answer[noAnswerMessageLength=18]");
    }
}
