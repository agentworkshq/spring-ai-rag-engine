## What and why

## Checklist

- [ ] `./mvnw verify -pl "!rag-engine-sample"` passes
- [ ] No Lombok
- [ ] Tests use no network and no real model
- [ ] Questions, answers and snippets are never logged or printed, also not in `toString`
- [ ] Every vector store search and delete includes the tenant filter built with `FilterExpressionBuilder`
- [ ] Errors use `ErrorMessages` with `RAG_E` codes that continue the numbering
- [ ] New endpoints have authentication, admin and tenant tests
- [ ] This does not fix an unreported vulnerability (see SECURITY.md)
