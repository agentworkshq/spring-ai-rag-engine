# spring-ai-rag-engine

A RAG engine library built on Spring AI.

```xml
<dependency>
    <groupId>dev.agentworkshq</groupId>
    <artifactId>rag-engine-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Persistence (optional)

By default ingestion state is kept in memory. Add `rag-engine-jpa` to store it in your database:

```xml
<dependency>
    <groupId>dev.agentworkshq</groupId>
    <artifactId>rag-engine-jpa</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

The schema ships as a Flyway repeatable migration, `R__rag_engine_schema.sql`, under `classpath:db/rag`. Host applications must add that location to their Flyway configuration:

```yaml
spring:
  flyway:
    locations: classpath:db/migration,classpath:db/rag
```

A repeatable migration has no version, so it never conflicts with the host's own versioned migrations, works on a database that already has applied migrations, and does not require `outOfOrder`. Flyway runs it after all versioned migrations and again whenever its checksum changes.

Every change to this script must stay idempotent (`CREATE TABLE IF NOT EXISTS`, `CREATE INDEX IF NOT EXISTS`) and additive. Do not drop or rename columns in it.

If the host declares its own `@EnableJpaRepositories` or `@EntityScan`, it must also include the `dev.agentworkshq.rag.jpa` packages.

## Notes

405 and 415 responses use Spring's default error body because the exception handler is scoped to this library's controllers only.

`RecursiveChunker` counts characters. `FixedTokenChunker` counts tokens and ignores overlap. `chunkSize` therefore means different units for the two strategies.

A document is re-ingested when its content, the chunking strategy, `chunkSize` or `overlap` changes. Changing the embedding model is not detected and needs a manual full re-ingest.
