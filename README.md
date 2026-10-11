# RAG System

A Spring Boot 4 PDF-based Retrieval-Augmented-Generation (RAG) chatbot. Upload PDFs, let the
system extract, chunk, embed and index them, then ask questions and receive answers grounded
strictly in your documents, with page-level source citations.

## Features

- PDF upload with validation (magic-byte check, size limits, extension/content-type) and live processing status.
- Text extraction preserving page numbers (Apache PDFBox), configurable chunking with overlap.
- Embeddings via any OpenAI-compatible embedding model; vectors stored in Qdrant.
- Retrieval-augmented answers from a configurable OpenAI-compatible LLM, with per-document isolation.
- Clear "not found in the PDF" response when context is insufficient; source snippets and page numbers otherwise.
- Persistent chat sessions and message history (Survive page refreshes).
- Per-user isolation of documents, sessions and vector retrieval via the `X-User-Id` header.
- Prompt-injection hardening: document text is treated as untrusted data, never as instructions.
- Layered architecture, DTOs, Bean Validation, centralized exception handling, Flyway migrations.
- Swagger UI / OpenAPI, Docker Compose, timeouts, retries and structured logging.

## Architecture

```
static UI (upload + chat)
        │  REST (X-User-Id)
        ▼
Controller → Service → Repository (JPA/Postgres)   [metadata, status, sessions, messages, chunks]
                    └→ EmbeddingClient  (OpenAI-compatible /embeddings)
                    └→ VectorStore      (Qdrant; in-memory for tests)
                    └→ LlmClient        (OpenAI-compatible /chat/completions)
```

Layers:

- `document` – `Document`/`DocumentChunk` entities, upload API and status.
- `ingestion` – PDF extraction, sanitization, chunking, async processing pipeline.
- `embedding` / `llm` – OpenAI-compatible HTTP clients with timeouts and retries.
- `vector` – `VectorStore` abstraction with `QdrantVectorStore` and `InMemoryVectorStore`.
- `rag` – prompt construction and the RAG answer pipeline.
- `chat` – sessions, messages and the chat API.
- `common` / `config` – error handling, request user resolution, configuration properties.

## Quick start (Docker Compose)

```bash
cp .env.example .env
# set OPENAI_API_KEY (or point OPENAI_BASE_URL at a local OpenAI-compatible server)
docker compose up --build
```

- UI: http://localhost:8080/
- Swagger UI: http://localhost:8080/swagger-ui.html

## Local development

```bash
docker compose up -d postgres qdrant
cp .env.example .env          # optional; export variables or use env
./gradlew bootRun
```

The app defaults to PostgreSQL on `localhost:5432` and Qdrant on `localhost:6333`, and reads
configuration from environment variables (see `.env.example`) or `application.yaml`.

## Configuration

All settings are under the `rag.*` prefix and can be overridden by environment variables.

| Property | Env | Default | Description |
| --- | --- | --- | --- |
| `rag.embedding.base-url` | `EMBEDDING_BASE_URL` / `OPENAI_BASE_URL` | `https://api.openai.com/v1` | Embedding endpoint |
| `rag.embedding.api-key` | `EMBEDDING_API_KEY` / `OPENAI_API_KEY` | – | API key (never commit) |
| `rag.embedding.model` | `EMBEDDING_MODEL` | `text-embedding-3-small` | Embedding model |
| `rag.embedding.dimension` | `EMBEDDING_DIMENSION` | `1536` | Vector size (must match model) |
| `rag.llm.base-url` | `LLM_BASE_URL` / `OPENAI_BASE_URL` | `https://api.openai.com/v1` | Chat endpoint |
| `rag.llm.model` | `LLM_MODEL` | `gpt-4o-mini` | Chat model |
| `rag.chunking.size` / `overlap` | `CHUNK_SIZE` / `CHUNK_OVERLAP` | `800` / `150` | Chunking |
| `rag.retrieval.top-k` | `RETRIEVAL_TOP_K` | `5` | Retrieved chunks |
| `rag.retrieval.min-score` | `RETRIEVAL_MIN_SCORE` | `0.2` | Similarity floor |
| `rag.vector-store.type` | `VECTOR_STORE_TYPE` | `qdrant` | `qdrant` or `memory` |
| `rag.qdrant.base-url` | `QDRANT_URL` | `http://localhost:6333` | Qdrant endpoint |

The embedding model, LLM, chunk sizes, top-K and thresholds are fully configurable.

## REST API

All endpoints are scoped by the `X-User-Id` request header (defaults to `anonymous`). Resources
owned by one user are invisible to others (404).

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/api/documents` | Upload a PDF (`multipart/form-data`, field `file`) |
| `GET` | `/api/documents` | List caller's documents |
| `GET` | `/api/documents/{id}` | Document + processing status |
| `DELETE` | `/api/documents/{id}` | Delete document, chunks and vectors |
| `POST` | `/api/chat/sessions` | Create session (`{ documentId?, title? }`) |
| `GET` | `/api/chat/sessions` | List caller's sessions |
| `GET` | `/api/chat/sessions/{id}` | Session with full message history |
| `DELETE` | `/api/chat/sessions/{id}` | Delete a session |
| `POST` | `/api/chat/sessions/{id}/messages` | Ask `{ question }`, get a grounded answer |

Example:

```bash
curl -H "X-User-Id: alice" -F file=@manual.pdf http://localhost:8080/api/documents
curl -H "X-User-Id: alice" -H "Content-Type: application/json" \
  -d '{"documentId":"<uuid>"}' http://localhost:8080/api/chat/sessions
curl -H "X-User-Id: alice" -H "Content-Type: application/json" \
  -d '{"question":"What is the warranty period?"}' \
  http://localhost:8080/api/chat/sessions/<sessionId>/messages
```

## Security notes

- Secrets are read from the environment only; nothing sensitive is committed.
- Uploads are validated by extension, content type, size and PDF magic bytes.
- Extracted PDF text is sanitized and always framed as untrusted data in the prompt; the system
  prompt forbids following instructions found inside documents (prompt-injection defense).
- Every document, session, message and vector retrieval is scoped to the owning user.
- Outbound HTTP calls have connect/read timeouts and bounded retries (4xx are not retried).

## Tests

```bash
./gradlew test
```

Tests run against H2 and the in-memory vector store with an embedded test PDF and mocked
embedding/LLM HTTP calls, covering upload validation, chunking, retrieval, RAG grounding,
session isolation/authorization and error handling.
