package dev.agentworkshq.rag.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@AutoConfiguration
@EnableConfigurationProperties(RagEngineProperties.class)
public class RagEngineAutoConfiguration {
}
