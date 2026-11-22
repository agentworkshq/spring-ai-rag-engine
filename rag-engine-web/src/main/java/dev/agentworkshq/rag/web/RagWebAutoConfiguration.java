package dev.agentworkshq.rag.web;

import dev.agentworkshq.rag.web.exception.RagExceptionHandler;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "rag-engine.web", name = "enabled", havingValue = "true")
public class RagWebAutoConfiguration {

    @Bean
    RagExceptionHandler ragExceptionHandler() {
        return new RagExceptionHandler();
    }
}
