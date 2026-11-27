package dev.agentworkshq.rag.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.agentworkshq.rag.core.answer.RagService;
import dev.agentworkshq.rag.core.ingest.IngestionService;
import dev.agentworkshq.rag.web.RagWebAutoConfiguration;
import dev.agentworkshq.rag.web.controller.RagControllers;
import dev.agentworkshq.rag.web.exception.RagExceptionHandler;
import dev.agentworkshq.rag.web.security.RequestGuard;
import dev.agentworkshq.rag.web.tenant.TenantResolver;
import dev.agentworkshq.rag.web.upload.UploadService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

class RagEngineSwitchTest {

    private static final List<Class<?>> CORE = List.of(RagEngineProperties.class, IngestionService.class,
            RagService.class);
    private static final List<Class<?>> WEB = List.of(RagControllers.class, RagExceptionHandler.class,
            RequestGuard.class, TenantResolver.class, UploadService.class);

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RagEngineAutoConfiguration.class, RagWebAutoConfiguration.class));

    private void assertState(boolean core, boolean web, String... properties) {
        runner.withPropertyValues(properties).run(context -> {
            assertThat(context).hasNotFailed();
            CORE.forEach(type -> assertThat(context.getBeanNamesForType(type).length > 0)
                    .as(type.getSimpleName()).isEqualTo(core));
            WEB.forEach(type -> assertThat(context.getBeanNamesForType(type).length > 0)
                    .as(type.getSimpleName()).isEqualTo(web));
        });
    }

    @Test
    void byDefaultTheCoreIsOnAndTheWebModuleIsOff() {
        assertState(true, false);
    }

    @Test
    void theWebModuleNeedsOnlyItsOwnSwitch() {
        assertState(true, true, "rag-engine.web.enabled=true", "rag-engine.web.upload.enabled=true");
    }

    @Test
    void bothSwitchesOffCreateNothing() {
        assertState(false, false, "rag-engine.enabled=false", "rag-engine.web.enabled=false");
    }

    @Test
    void theMainSwitchAlsoTurnsTheWebModuleOff() {
        assertState(false, false, "rag-engine.enabled=false", "rag-engine.web.enabled=true",
                "rag-engine.web.upload.enabled=true");
    }
}
