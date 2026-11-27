package dev.agentworkshq.rag.jpa;

import static org.assertj.core.api.Assertions.assertThat;

import dev.agentworkshq.rag.core.store.DocumentRegistry;
import dev.agentworkshq.rag.core.store.IngestionJobStore;
import dev.agentworkshq.rag.jpa.repository.SourceDocumentRepository;
import dev.agentworkshq.rag.jpa.store.JpaDocumentRegistry;
import dev.agentworkshq.rag.jpa.store.JpaIngestionJobStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.transaction.autoconfigure.TransactionAutoConfiguration;

class RagJpaSwitchTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RagJpaAutoConfiguration.class));

    private void assertEnabled(String... properties) {
        runner.withConfiguration(AutoConfigurations.of(DataSourceAutoConfiguration.class,
                        DataSourceTransactionManagerAutoConfiguration.class, TransactionAutoConfiguration.class,
                        HibernateJpaAutoConfiguration.class, DataJpaRepositoriesAutoConfiguration.class))
                .withPropertyValues("spring.datasource.url=jdbc:h2:mem:switch",
                        "spring.jpa.hibernate.ddl-auto=create-drop")
                .withPropertyValues(properties)
                .run(context -> assertThat(context).hasNotFailed()
                        .hasSingleBean(JpaDocumentRegistry.class)
                        .hasSingleBean(JpaIngestionJobStore.class));
    }

    @Test
    void isEnabledWhenThePropertyIsMissing() {
        assertEnabled();
    }

    @Test
    void isEnabledWhenThePropertyIsTrue() {
        assertEnabled("rag-engine.enabled=true");
    }

    @Test
    void createsNoBeanAndRegistersNoPackageWhenThePropertyIsFalse() {
        runner.withPropertyValues("rag-engine.enabled=false").run(context -> {
            assertThat(context).hasNotFailed()
                    .doesNotHaveBean(RagJpaAutoConfiguration.class)
                    .doesNotHaveBean(DocumentRegistry.class)
                    .doesNotHaveBean(IngestionJobStore.class)
                    .doesNotHaveBean(SourceDocumentRepository.class);
            assertThat(AutoConfigurationPackages.has(context.getBeanFactory())).isFalse();
        });
    }
}
