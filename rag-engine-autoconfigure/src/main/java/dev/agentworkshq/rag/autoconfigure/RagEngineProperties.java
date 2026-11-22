package dev.agentworkshq.rag.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "rag-engine")
public record RagEngineProperties(@DefaultValue("true") boolean enabled) {
}
