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

## REST endpoints

The web module is opt-in and only for servlet web applications. Enable it with `rag-engine.web.enabled=true`.

| Method and path (under `basePath`) | Access | Description |
|---|---|---|
| `POST /ask` | authenticated | Ask a question. Body: `question`, optional `sourceNames` (max 20), optional `topK` (1 to 50). |
| `POST /sources/{name}/ingest` | admin | Run ingestion for a configured source, synchronously. |
| `GET /sources/{name}/jobs?limit=` | admin | Recent jobs for a source. `limit` defaults to 10, maximum 50. |
| `GET /jobs/{id}` | admin | One job. |

Source names may contain letters, digits, dash, underscore and dot, up to 100 characters.

| Property | Default | Description |
|---|---|---|
| `rag-engine.web.enabled` | `false` | Registers the endpoints. |
| `rag-engine.web.base-path` | `/api/rag` | Prefix for all endpoints. |
| `rag-engine.web.require-authentication` | `true` | Rejects requests without a principal with 401. |
| `rag-engine.web.admin-role` | `RAG_ADMIN` | Role needed for admin endpoints, 403 otherwise. Blank disables the role check. |
| `rag-engine.web.tenant.mode` | `SINGLE` | `SINGLE` or `CUSTOM`. |
| `rag-engine.web.tenant.default-tenant` | `default` | Tenant used in `SINGLE` mode. |

The question length limit is `rag-engine.answer.max-question-length`.

Tenants: the tenant id never comes from the request body, query string or headers. In `SINGLE` mode every request uses `tenant.default-tenant`. In `CUSTOM` mode the application must provide a `TenantResolver` bean, otherwise startup fails.

Authentication: the library has no Spring Security dependency. It checks `HttpServletRequest.getUserPrincipal()` and `isUserInRole(adminRole)`, so any mechanism that fills those works. CORS and CSRF are the host application's job.

Warning: with `rag-engine.web.require-authentication=false` all endpoints, including ingestion and job endpoints, are open to anyone who can reach the application. Use it for local demos only.

Jobs are not tenant scoped yet, which is why the job endpoints are admin only.

## Upload endpoints

Document upload is off by default. Enable it with `rag-engine.web.upload.enabled=true` on top of the web module. Uploaded documents use the reserved source name `uploads`, which a configured source cannot take, and the tenant comes from the `TenantResolver`.

| Method and path (under `basePath`) | Description |
|---|---|
| `POST /documents` | `multipart/form-data` with a part named `file`. 201 with a `Location` header when added, 200 when updated or unchanged. |
| `GET /documents?page=&size=` | The tenant's documents with page metadata. `size` defaults to 20, maximum 100. |
| `DELETE /documents/{externalId}` | Removes the document and its chunks. 204, or 404 when unknown. |

| Property | Default | Description |
|---|---|---|
| `rag-engine.web.upload.enabled` | `false` | Registers the upload endpoints. |
| `rag-engine.web.upload.require-admin` | `true` | Needs the admin role when authentication is required. When false, any authenticated user may upload. |
| `rag-engine.web.upload.max-documents-per-tenant` | `1000` | Replacing an existing document is always allowed. |

The size cap is `rag-engine.limits.max-file-size`. The host application must also set `spring.servlet.multipart.max-file-size` and `spring.servlet.multipart.max-request-size`, because the container limits apply before this library sees the request.

Safety rules:
- Only the last path segment of the file name is kept. Empty names, control characters, names over 200 characters, names starting with a dot and names containing `..` are rejected. The name is the document id and is never used to build a file system path.
- The extension must be one the document loader supports.
- A `.pdf` must start with the `%PDF` magic bytes.
- The upload is streamed into a randomly named temp file with a hard size cap, and the temp file is always deleted.
- The document count per tenant is checked before ingesting.

## Notes

405 and 415 responses use Spring's default error body because the exception handler is scoped to this library's controllers only.

`RecursiveChunker` counts characters. `FixedTokenChunker` counts tokens and ignores overlap. `chunkSize` therefore means different units for the two strategies.

The ingestion lock works inside one JVM only. When running several instances, use an external scheduler or lock so only one instance ingests a given source and tenant at a time.

If more than one `VectorStore` bean exists, mark one of them `@Primary`.

A document is re-ingested when its content, the chunking strategy, `chunkSize` or `overlap` changes. Changing the embedding model is not detected and needs a manual full re-ingest.
