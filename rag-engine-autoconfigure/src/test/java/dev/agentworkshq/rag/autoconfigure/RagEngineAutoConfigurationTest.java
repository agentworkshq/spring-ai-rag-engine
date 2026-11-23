package dev.agentworkshq.rag.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.agentworkshq.rag.core.answer.RagContext;
import dev.agentworkshq.rag.core.answer.RagService;
import dev.agentworkshq.rag.core.constants.ErrorMessages;
import dev.agentworkshq.rag.core.exception.ServiceException;
import dev.agentworkshq.rag.core.ingest.IngestionService;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RagEngineAutoConfigurationTest {

    @TempDir
    Path docs;

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(RagEngineAutoConfiguration.class))
                .withPropertyValues(
                        "rag-engine.sources[0].name=docs",
                        "rag-engine.sources[0].path=" + docs.toString().replace('\\', '/'));
    }

    @Test
    void contextStartsWithoutVectorStoreAndIngestFailsClearly() {
        runner().run(context -> {
            assertThat(context).hasNotFailed();
            IngestionService service = context.getBean(IngestionService.class);

            assertThatThrownBy(() -> service.ingest("docs", "t1"))
                    .isInstanceOf(ServiceException.class)
                    .satisfies(e -> assertThat(((ServiceException) e).getErrorMessage())
                            .isEqualTo(ErrorMessages.RAG_E019))
                    .hasMessageContaining("vector store starter");
        });
    }

    @Test
    void askFailsClearlyWithoutVectorStore() {
        runner().run(context -> {
            RagService service = context.getBean(RagService.class);

            assertThatThrownBy(() -> service.ask("question", RagContext.of("t1")))
                    .isInstanceOf(ServiceException.class)
                    .satisfies(e -> assertThat(((ServiceException) e).getErrorMessage())
                            .isEqualTo(ErrorMessages.RAG_E019));
        });
    }
}
