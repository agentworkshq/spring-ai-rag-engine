# Contributing

## Build

You need Java 21.

Build and test the library modules, which needs no Docker:

```bash
./mvnw verify -pl "!rag-engine-sample"
```

The sample needs Docker and Ollama, as described in the [README](README.md#try-the-sample).

## Rules

- No Lombok.
- Tests use no network and no real model.
- Never log or print questions, answers or snippets, and keep them out of `toString`.
- Every vector store search and delete includes the tenant filter, built with `FilterExpressionBuilder`.
- Errors use `ErrorMessages` with `RAG_E` codes. New codes continue the numbering.
- New endpoints need tests for authentication, the admin role and tenant isolation.

## Vulnerabilities

Do not open a public issue or pull request for a security problem. Report it as described in [SECURITY.md](SECURITY.md).
