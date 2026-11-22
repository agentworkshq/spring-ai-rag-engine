# spring-ai-rag-engine

A RAG engine library built on Spring AI.

```xml
<dependency>
    <groupId>dev.agentworkshq</groupId>
    <artifactId>rag-engine-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Notes

405 and 415 responses use Spring's default error body because the exception handler is scoped to this library's controllers only.
