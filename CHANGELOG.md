# Changelog

All notable changes to this project are documented in this file. The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project uses [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - Not yet released

### Added

- Filesystem source with include patterns that skips hidden files and files outside its root. Custom sources through `RagSource` beans.
- Document loaders for text and Markdown, and for PDF and Office, OpenDocument, RTF and HTML files when the optional Spring AI readers are present.
- Limits for file size and PDF page count.
- Recursive character chunker and fixed token chunker.
- Hash based change detection that adds, updates, skips and deletes documents, with a guard against empty scans.
- Per-document ingest, delete and list operations.
- Ingestion jobs and a document registry, kept in memory by default.
- Retrieval and answering with citations, a no-answer message, optional query rewriting and a pluggable reranker.
- Tenant filtering on vector store searches and deletes.
- Optional REST endpoints for ask, ingestion, jobs and documents (upload, list, get, delete), with authentication and admin role checks.
- JPA persistence module for the document registry and jobs, with a Flyway schema script.
- Spring Boot auto-configuration and a starter.
- Sample application using pgvector and Ollama.
