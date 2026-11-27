# spring-ai-rag-engine

## What it is

A retrieval augmented generation library for Spring Boot, built on Spring AI.
It ingests documents from configured sources and uploads into your vector store, and answers questions with cited sources.
It adds change detection, tenant scoping and optional REST endpoints, so you do not have to write the plumbing yourself.

What it does not do:

- It does not ship a chat model, an embedding model or a vector store. You bring your own through Spring AI.
- It does not authenticate users, limit request rates or configure CORS and CSRF.
- It does not schedule ingestion or run it in the background. You trigger it.
- It has no user interface.

## Quick start

### Use it in your app

The artifacts are not on Maven Central yet. Run `./mvnw install -DskipTests` in this repository to put them in your local Maven repository.

```xml
<dependency>
    <groupId>dev.agentworkshq</groupId>
    <artifactId>rag-engine-spring-boot-starter</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>dev.agentworkshq</groupId>
    <artifactId>rag-engine-web</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

You also need a Spring AI chat model, an embedding model and a vector store on the classpath. If more than one `VectorStore` bean exists, mark one of them `@Primary`.

Minimal configuration:

```yaml
rag-engine:
  web:
    enabled: true
  sources:
    - name: docs
      type: filesystem
      path: ./docs
```

The endpoints then live under `/api/rag` and require an authenticated user. See [Configuration reference](#configuration-reference) for all properties.

### Try the sample

`rag-engine-sample` runs the library against PostgreSQL with pgvector and a local Ollama model. Authentication is switched off in the sample, so use it for local demos only.

Prerequisites:
- Java 21
- Docker with Compose
- about 6 GB of free RAM
- the first start downloads about 2 GB of models (llama3.2 and nomic-embed-text), so it takes a few minutes

Start the database and Ollama, then the application:

```bash
docker compose -f rag-engine-sample/compose.yaml up -d
./mvnw install -DskipTests
cd rag-engine-sample && ../mvnw spring-boot:run
```

On Windows PowerShell:

```powershell
docker compose -f rag-engine-sample/compose.yaml up -d
.\mvnw.cmd install -DskipTests
cd rag-engine-sample; ..\mvnw.cmd spring-boot:run
```

The application listens on `http://localhost:8080`. Run the examples below from a second terminal in the `rag-engine-sample` directory. Stop everything with `docker compose -f compose.yaml down` (add `-v` to delete the data).

Ingest the documents in `docs-sample`:

```bash
curl -X POST http://localhost:8080/api/rag/sources/docs/ingest
```

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/rag/sources/docs/ingest
```

Ask a question:

```bash
curl -X POST http://localhost:8080/api/rag/ask \
  -H "Content-Type: application/json" \
  -d '{"question": "How do I reset the device?"}'
```

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/rag/ask `
  -ContentType "application/json" `
  -Body '{"question": "How do I reset the device?"}' | ConvertTo-Json -Depth 10
```

Upload a document:

```bash
curl -F "file=@docs-sample/warranty-and-support.txt" http://localhost:8080/api/rag/documents
```

```powershell
curl.exe -F "file=@docs-sample/warranty-and-support.txt" http://localhost:8080/api/rag/documents
```

List uploaded documents:

```bash
curl "http://localhost:8080/api/rag/documents?page=0&size=20"
```

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/rag/documents?page=0&size=20" | ConvertTo-Json -Depth 10
```

Delete an uploaded document:

```bash
curl -X DELETE http://localhost:8080/api/rag/documents/warranty-and-support.txt
```

```powershell
Invoke-RestMethod -Method Delete -Uri http://localhost:8080/api/rag/documents/warranty-and-support.txt
```

## Concepts

- Source: a named origin of documents. The library ships a filesystem source. Any `RagSource` bean is registered as well. Names must be unique, and `uploads` is reserved for uploaded documents.
- Document: one item of a source, identified by its source name and external id. For the filesystem source the external id is the path relative to the source root.
- Chunk: a piece of a document stored in the vector store, with metadata for tenant, source, document, file name, chunk index, content hash and, for PDF, page.
- Tenant: every chunk and registry entry belongs to one tenant.
- Registry and jobs: the registry records which documents are ingested and with which hash. A job records one ingestion run of a source. Both are kept in memory unless [Persistence](#persistence) is added.

## Ingestion

`IngestionService.ingest(sourceName, tenantId)` scans a source and brings the vector store in line with it. Documents are added, updated, skipped or deleted, and the result counts each of these plus failures. One failed document does not stop the run.

`IngestionService` also works on single documents: `ingestDocument`, `deleteDocument` and `listDocuments`.

### Filesystem source

The root directory must exist at startup. Hidden files and directories are skipped, as are files that resolve outside the root. `include` takes glob patterns matched against the relative path, must not contain `..`, and matches every file when empty.

### Document loaders and limits

| Extensions | Reader | Dependency to add |
|---|---|---|
| `txt`, `text`, `md`, `markdown` | Read as UTF-8 text | none |
| `pdf` | One document per page | `org.springframework.ai:spring-ai-pdf-document-reader` |
| `docx`, `doc`, `pptx`, `ppt`, `xlsx`, `xls`, `odt`, `rtf`, `html`, `htm` | Apache Tika | `org.springframework.ai:spring-ai-tika-document-reader` |

Any other extension fails for that document. `rag-engine.limits.max-file-size` applies to every file and `rag-engine.limits.max-pages` to PDF files.

### Chunking

`RecursiveChunker` counts characters. `FixedTokenChunker` counts tokens and ignores overlap. `chunkSize` therefore means different units for the two strategies.

### Change detection

A document is re-ingested when its content, the chunking strategy, `chunkSize` or `overlap` changes. Changing the embedding model is not detected and needs a manual full re-ingest. Unchanged documents are skipped.

Documents that are no longer in the source are removed. A scan that fails removes nothing. The filesystem source fails the whole run when it cannot visit a file or list a directory, unless that file or directory is hidden. If a source returns nothing but has registered documents, the run fails and deletes nothing, unless `rag-engine.ingestion.allow-empty-source` is true.

Only one ingestion operation can run for a source and tenant at a time. A second one fails with a conflict.

## Retrieval and answers

`RagService.ask(question, context)` takes a `RagContext` with the tenant id, optional source names and an optional `topK`.

1. The question is checked against `rag-engine.answer.max-question-length`.
2. With `rag-engine.retrieval.query-rewrite`, the chat model first rewrites the question into a search query.
3. The vector store is searched with a tenant filter, a source filter when source names are given, and the similarity threshold. The requested `topK` is capped at `rag-engine.retrieval.max-top-k`.
4. A `Reranker` bean can reorder the results. The default keeps the order.
5. Numbered chunks are added to the prompt up to `rag-engine.answer.max-context-chars`.
6. The model is told to answer from those chunks only and to cite them as `[1]`, `[2]`.

The answer carries the text, an `answered` flag and one citation per chunk sent to the model: number, document id, file name, page, a snippet of up to 200 characters and `used`, which says whether the answer refers to that number. With `rag-engine.answer.cite-sources=false` the list is empty.

When nothing is retrieved, the model is not asked to answer and the reply is `rag-engine.answer.no-answer-message` with `answered=false`. The same flag is false when the model replies with exactly that message.

## REST endpoints

The web module is opt-in and only for servlet web applications. Enable it with `rag-engine.web.enabled=true`.

| Method and path (under `basePath`) | Access | Description |
|---|---|---|
| `POST /ask` | authenticated | Ask a question. Body: `question`, optional `sourceNames` (max 20), optional `topK` (1 to 50). |
| `POST /sources/{name}/ingest` | admin | Run ingestion for a configured source, synchronously. |
| `GET /sources/{name}/jobs?limit=` | admin | Recent jobs for a source. `limit` defaults to 10, maximum 50. |
| `GET /jobs/{id}` | admin | One job. |

Source names in the path may contain letters, digits, dash, underscore and dot, up to 100 characters.

`POST /sources/{name}/ingest` is synchronous, so a large source can hit HTTP timeouts in clients and proxies.

Tenants: in `SINGLE` mode every request uses `rag-engine.web.tenant.default-tenant`. In `CUSTOM` mode the application must provide a `TenantResolver` bean, otherwise startup fails.

Authentication: the library has no Spring Security dependency. It checks `HttpServletRequest.getUserPrincipal()` and `isUserInRole(adminRole)`, so any mechanism that fills those works. CORS and CSRF are the host application's job.

Warning: with `rag-engine.web.require-authentication=false` all endpoints, including ingestion and job endpoints, are open to anyone who can reach the application. Use it for local demos only.

Jobs are not tenant scoped yet, which is why the job endpoints are admin only.

405 and 415 responses use Spring's default error body because the exception handler is scoped to this library's controllers only.

## Upload endpoints

Document upload is off by default. Enable it with `rag-engine.web.upload.enabled=true` on top of the web module. Uploaded documents use the reserved source name `uploads`, which a configured source cannot take. The endpoints need the admin role unless `rag-engine.web.upload.require-admin` is false.

| Method and path (under `basePath`) | Description |
|---|---|
| `POST /documents` | `multipart/form-data` with a part named `file`. 201 with a `Location` header when added, 200 when updated or unchanged. |
| `GET /documents?page=&size=` | The tenant's documents with page metadata. `size` defaults to 20, maximum 100. |
| `GET /documents/{externalId}` | One document of the tenant. 404 when unknown. |
| `DELETE /documents/{externalId}` | Removes the document and its chunks. 204, or 404 when unknown. |

The size cap is `rag-engine.limits.max-file-size`. The host application must also set `spring.servlet.multipart.max-file-size` and `spring.servlet.multipart.max-request-size`, because the container limits apply before this library sees the request.

Safety rules:
- Only the last path segment of the file name is kept. Empty names, control characters, names over 200 characters, names starting with a dot and names containing `..` are rejected. The name is the document id and is never used to build a file system path.
- The extension must be one the document loader supports.
- A `.pdf` must start with the `%PDF` magic bytes.
- The upload is streamed into a randomly named temp file with a hard size cap, and the temp file is always deleted.
- The document count per tenant is checked before ingesting. Replacing an existing document is always allowed.

## Persistence

Persistence is optional. By default ingestion state is kept in memory. Add `rag-engine-jpa` to store it in your database:

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

## Configuration reference

The `rag-engine.web.*` properties need the `rag-engine-web` module.

| Property | Type | Default | Meaning |
|---|---|---|---|
| `rag-engine.enabled` | boolean | `true` | When false, the core auto-configuration creates no beans: no ingestion, answering, sources or in-memory stores. The web and JPA modules are not switched off by it. Keep `rag-engine.web.enabled` false as well, because the endpoints need those beans and startup fails without them. The JPA module has no property and stays active while it is on the classpath. |
| `rag-engine.chunking.strategy` | `FIXED` or `RECURSIVE` | `RECURSIVE` | How documents are split. |
| `rag-engine.chunking.chunk-size` | int | `800` | Chunk size, in characters for `RECURSIVE` and tokens for `FIXED`. Must be positive. |
| `rag-engine.chunking.overlap` | int | `100` | Characters repeated from the previous chunk, ignored by `FIXED`. Not negative and smaller than the chunk size, for both strategies. |
| `rag-engine.limits.max-file-size` | data size | `20MB` | Largest file that is ingested or uploaded. Must be positive. |
| `rag-engine.limits.max-pages` | int | `500` | Largest page count of a PDF. Must be positive. |
| `rag-engine.ingestion.batch-size` | int | `50` | Chunks per call to the vector store. Must be positive. |
| `rag-engine.ingestion.allow-empty-source` | boolean | `false` | Lets an empty scan delete all registered documents of the source. |
| `rag-engine.retrieval.top-k` | int | `6` | Chunks retrieved when the request names no `topK`. Positive, at most `max-top-k`. |
| `rag-engine.retrieval.max-top-k` | int | `20` | Upper bound for any `topK`. |
| `rag-engine.retrieval.similarity-threshold` | double | `0.65` | Minimum similarity of a chunk, from 0 to 1. |
| `rag-engine.retrieval.query-rewrite` | boolean | `false` | Rewrites the question with the chat model before the search. |
| `rag-engine.answer.cite-sources` | boolean | `true` | Returns citations with the answer. |
| `rag-engine.answer.no-answer-message` | string | `I could not find the answer in the provided documents.` | Reply when there is no answer. Must not be blank. |
| `rag-engine.answer.max-context-chars` | int | `12000` | Most characters of context in the prompt. Must be positive. |
| `rag-engine.answer.max-question-length` | int | `2000` | Longest accepted question in characters. Must be positive. |
| `rag-engine.sources[].name` | string | required | Name of the source. |
| `rag-engine.sources[].type` | `FILESYSTEM` | `FILESYSTEM` | Kind of source. |
| `rag-engine.sources[].path` | string | required | Root directory of the source. |
| `rag-engine.sources[].include` | list of strings | none | Glob patterns for files to ingest. None means all files. |
| `rag-engine.web.enabled` | boolean | `false` | Registers the endpoints. |
| `rag-engine.web.base-path` | string | `/api/rag` | Prefix for all endpoints. Starts with `/` and does not end with it. |
| `rag-engine.web.require-authentication` | boolean | `true` | Rejects requests without a principal with 401. |
| `rag-engine.web.admin-role` | string | `RAG_ADMIN` | Role needed for admin endpoints, 403 otherwise. Blank disables the role check. |
| `rag-engine.web.tenant.mode` | `SINGLE` or `CUSTOM` | `SINGLE` | Where the tenant comes from. |
| `rag-engine.web.tenant.default-tenant` | string | `default` | Tenant used in `SINGLE` mode. Must not be blank. |
| `rag-engine.web.upload.enabled` | boolean | `false` | Registers the upload endpoints. |
| `rag-engine.web.upload.require-admin` | boolean | `true` | Needs the admin role when authentication is required. When false, any authenticated user may upload. |
| `rag-engine.web.upload.max-documents-per-tenant` | int | `1000` | Most uploaded documents per tenant. Must be positive. |

## Security and privacy

### Tenant isolation

- Every vector store search and delete by this library includes a tenant filter built with `FilterExpressionBuilder`.
- In the REST endpoints the tenant comes only from the `TenantResolver`, never from the request body, query string or headers read by the library. A blank tenant id, one over 100 characters or one with control characters is refused.
- Code that calls `RagService` or `IngestionService` directly passes the tenant id itself and is responsible for it.

### Retrieved text

Retrieved text is untrusted. It is placed between `<context>` tags in the user message, the tags are escaped inside chunk text, file names are cleaned before they appear there, and the system prompt tells the model not to follow instructions found in the context.

### Uploads and files

Uploads are off by default, confined to a temp file and the `uploads` source, and capped in size and in number per tenant. The rules are listed under [Upload endpoints](#upload-endpoints). The filesystem source skips files that resolve outside its root.

### Logging

- `toString` of the library's records, properties included, never prints text such as questions, answers, snippets, file names, tenant ids, error messages or property values, only numbers, statuses, counts and lengths. Exceptions are different: they print their message. JSON responses are not affected.
- At info, warn and error level the library's own log messages contain only error codes, counts, durations, flags and class names.
- Source names, tenant ids, file names, file paths, request paths, exception messages and stack traces are logged at debug level only. Exception messages from the vector store, the models and the document readers can repeat input text, so treat debug logs as sensitive.

### Error responses

Server error responses carry a generic message. Client error responses can echo a name sent by the caller or the tenant id, without control and bidirectional characters and cut to 64 characters.

### What the library cannot protect against

- Prompt injection. Delimiters and instructions lower the risk but a model can still follow text inside a document. Do not treat answers as trusted, and do not let them trigger actions without a check.
- A wrong `TenantResolver`, or a vector store that does not apply the filter correctly.
- Anyone who can write to a source directory, the vector store or the database.
- Data sent to your model and embedding providers, which includes questions and document text.
- Malicious files that exploit a document parser or make it use large amounts of CPU or memory. Parsers run inside your application.
- Request floods and model cost. `/ask` has no rate limiting; adding it is the host application's responsibility.

The host application must still take care of:

- access logs, because document URLs contain the file name
- request and response body logging, because bodies carry questions, answers, snippets and uploaded files
- reverse proxies, gateways and tracing tools that record URLs, headers or bodies
- debug logging and observability settings of Spring and Spring AI, which can record requests and prompts

Report vulnerabilities as described in [SECURITY.md](SECURITY.md).

## Known limitations

Ingestion

- A failed re-ingest can leave a document without chunks until the next successful run, because the old chunks are deleted first.
- With the in-memory registry and a persistent vector store, files deleted while the application was down stay searchable, and earlier uploads can no longer be listed or deleted. Use the JPA module together with a persistent store.
- One file or directory that cannot be visited stops ingestion of its whole source until it is fixed or hidden.
- Removing a source from the configuration does not delete its chunks.
- Changing the embedding model is not detected.
- Ingestion and its jobs run synchronously.
- The ingestion lock works inside one JVM only. When running several instances, use an external scheduler or lock so only one instance ingests a given source and tenant at a time.
- A job stays `RUNNING` if the application stops during the run.
- The in-memory job store is never pruned.
- With the JPA module, source names and external ids are limited to 255 characters.

Documents and uploads

- Document parsers have no time limit, and the page limit applies to PDF only.
- Each file is read into memory while it is parsed.
- The upload quota is not atomic, so concurrent uploads can exceed it.
- Uploads and deletes of one tenant do not run in parallel. A concurrent one fails with 409.
- The document list is loaded in full before it is paged.

Access and retrieval

- The job endpoints are not tenant scoped.
- The tenant filter was checked against `SimpleVectorStore` and the pgvector filter converter only.

## Roadmap

No roadmap is committed yet. The known limitations above are the open items.

## Building from source

You need Java 21.

Build and test the library modules, without Docker:

```bash
./mvnw verify -pl "!rag-engine-sample"
```

The sample needs Docker and Ollama as described under [Try the sample](#try-the-sample). Its Docker tests are skipped unless the `docker-tests` profile is active.

See [CONTRIBUTING.md](CONTRIBUTING.md) before sending a change.

## Optional readers and their licenses

The Tika and PDF readers are optional dependencies. They are not in the published jars and are not pulled in by this library. Add them to your application only if you need them.

The Tika reader brings transitive libraries under other licenses than Apache 2.0. With Spring AI 2.0.1 their published POMs declare, among others:

| Library | Declared license |
|---|---|
| `com.github.junrar:junrar` | UnRar License |
| `com.github.albfernandez:juniversalchardet` | MPL 1.1, GPL 3.0, LGPL 3.0 |
| `org.codelibs:jhighlight` | CDDL 1.0, LGPL 2.1 or later |
| `org.bouncycastle:bcprov-jdk18on`, `bcpkix-jdk18on`, `bcutil-jdk18on`, `bcjmail-jdk18on` | Bouncy Castle Licence |
| `org.glassfish.jaxb:jaxb-runtime`, `jaxb-core`, `txw2`, `jakarta.xml.bind:jakarta.xml.bind-api`, `jakarta.activation:jakarta.activation-api`, `org.eclipse.angus:angus-activation`, `com.sun.istack:istack-commons-runtime` | Eclipse Distribution License 1.0 |
| `com.adobe.xmp:xmpcore`, `org.ow2.asm:asm`, `org.tallison:jmatio`, `com.github.virtuald:curvesapi`, `com.github.jai-imageio:jai-imageio-core` | BSD variants, the one of `jai-imageio-core` with a nuclear disclaimer |
| `com.googlecode.plist:dd-plist`, `org.jsoup:jsoup`, `org.brotli:dec` | MIT |
| `org.tukaani:xz` | 0BSD |
| `org.jdom:jdom2` | Apache-like license of its own |

If you add the Tika reader, review these and the rest of its dependency tree for your own use. This list is not legal advice.

## License

Apache License 2.0, see [LICENSE](LICENSE).
