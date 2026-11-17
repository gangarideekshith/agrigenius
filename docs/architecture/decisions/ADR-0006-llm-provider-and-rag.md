# ADR-0006: LLM provider, embeddings and RAG for the agriculture chatbot

- Status: Accepted (decisions C1, C2, C4, C8, C9; 2026-11-17). Live spike **pending an `ANTHROPIC_API_KEY`**.

## Context

PROJECT_SPEC §3.3 requires a Spring AI chatbot with chat memory, tool calling and retrieval-augmented generation (RAG) over a vector store, in English, Hindi, Telugu, Kannada and Tamil. The backend runs Spring Boot 4.1.2 (ADR-0002) with PostgreSQL 17 + pgvector 0.8.7.

## Decision

- **Framework:** Spring AI **2.0.2** (released 2026-10-22), whose modules are built against Spring Boot **4.1.2**. Spring AI 2.1 is still a release candidate.
- **Chat model:** `spring-ai-starter-model-anthropic`, which wraps the official Anthropic Java SDK (`anthropic-java-core` 2.72.0). Model **`claude-opus-5-5`** with effort **`low`** (chat rarely benefits from more), set by `CHAT_MODEL` and `CHAT_EFFORT`.
- **Estimated cost per message** at the planned limits (~4K input tokens, ~400 output tokens): Opus 5.5 ≈ **$0.024**, Sonnet 5.5 ≈ $0.012, Haiku 5.5 ≈ $0.0006. Prompt caching of the stable system prompt lowers these. Choosing a cheaper model is the owner's decision; compare Indic-language quality in the live evaluation first.
- **Provider off by default:** when `ANTHROPIC_API_KEY` is empty, the chat endpoints return `503 CHAT_DISABLED` and the rest of the application starts and works normally (CI and developers without a key are unaffected). The key lives only in the backend environment.
- **Embeddings:** in-process via our own ONNX Runtime model `OnnxE5EmbeddingModel` (masked mean pooling + L2 normalisation in plain Java; Spring AI's `TransformersEmbeddingModel` is not used because it needs DJL's PyTorch engine, which downloads libtorch at runtime, and its auto-configuration collides with a custom `embeddingModel` bean). Enable with `EMBEDDING_PROVIDER=e5-onnx` and `VECTOR_STORE_TYPE=pgvector`. Model pinned to Hugging Face commit `614241f6` (MIT licence), SHA-256 verified before use, ~470 MB ONNX + 17 MB tokenizer, cached in `EMBEDDING_MODEL_CACHE_DIR` with **`intfloat/multilingual-e5-small`** (384 dimensions; covers hi/te/kn/ta). Farmer text never leaves the server for embedding; no extra key or service. The model is downloaded once and cached (`EMBEDDING_MODEL_CACHE_DIR`).
- **Vector store:** Spring AI `PgVectorStore` over the table `knowledge_chunks`, created by **Flyway** (`initialize-schema=false`) with an **HNSW** index using cosine distance.
- **Chat memory:** an own `ChatMemoryRepository` over `chat_messages` (last 10 messages of the session), **not** Spring AI's JDBC chat-memory table, which has no owner column and is not Flyway-managed.
- **Response mode:** non-streaming JSON in v1 (C4); SSE streaming later with the UI.
- **Limits (C9):** 20 messages per farmer per 10 minutes, 40 per day, 1,024 output tokens per answer, a global daily token budget (`CHAT_DAILY_TOKEN_BUDGET`).

## Alternatives considered

- **OpenAI and other Spring AI starters:** they pull in Reactor Netty (NIO), which fails on the development machine without a JVM workaround, and offer no advantage for this use case.
- **Embeddings in the Python ML service:** adds a network hop and Python model dependencies.
- **A paid embeddings API:** farmer questions would leave the server a second time, with another key and bill.
- **Spring AI JDBC chat memory:** no ownership column; schema not under Flyway.

## Consequences

- Spring AI 2.0.2 pins `anthropic-java-core` 2.72.0 (latest is 3.9.0). A **spike** must confirm adaptive thinking, effort, tool calling and refusal handling with `claude-opus-5-5` through Spring AI. If any fails, the official SDK is called directly behind the same `ChatModel` interface.
- First start downloads the embedding model (roughly 120–470 MB depending on the ONNX variant).
- Chat text is sent to Anthropic (privacy disclosure, decision C7).
- Live-model tests cost money and are opt-in (about $1 approved for the spike and first evaluation, C8).


## Verified 2026-11-17

- 384-dimension output confirmed with the real model.
- Cross-lingual sanity: Telugu and Hindi rice-pest questions score higher on an English rice passage than on unrelated text; in a full-app check paddy ranked first for en/hi/te queries.
- **Threshold warning:** e5 scores unrelated text around 0.72–0.77, so the provisional `CHAT_SIMILARITY_THRESHOLD=0.75` lets weak matches through. Tune it on the gold set (or use a top-vs-rest margin) before relying on retrieval.
