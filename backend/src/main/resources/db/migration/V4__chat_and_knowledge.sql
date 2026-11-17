-- AI agriculture chatbot (Milestone 5, ADR-0006/0007): farmer chat sessions and messages,
-- the curated knowledge base (documents + pgvector chunks) and per-user daily usage.

CREATE TABLE chat_sessions (
    id               UUID PRIMARY KEY,
    user_id          UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title            VARCHAR(120),
    language         VARCHAR(5)   NOT NULL
        CONSTRAINT ck_chat_sessions_language CHECK (language IN ('en', 'hi', 'te', 'kn', 'ta')),
    status           VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE'
        CONSTRAINT ck_chat_sessions_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    message_count    INT          NOT NULL DEFAULT 0 CHECK (message_count >= 0),
    created_at       TIMESTAMPTZ  NOT NULL,
    last_message_at  TIMESTAMPTZ,
    version          BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX ix_chat_sessions_user ON chat_sessions (user_id, last_message_at DESC NULLS LAST);

CREATE TABLE chat_messages (
    id                 UUID PRIMARY KEY,
    session_id         UUID         NOT NULL REFERENCES chat_sessions (id) ON DELETE CASCADE,
    user_id            UUID         NOT NULL,     -- denormalised for owner-scoped queries
    role               VARCHAR(10)  NOT NULL
        CONSTRAINT ck_chat_messages_role CHECK (role IN ('USER', 'ASSISTANT')),
    status             VARCHAR(12)  NOT NULL
        CONSTRAINT ck_chat_messages_status CHECK (status IN ('OK', 'FAILED', 'REFUSED', 'NO_SOURCES')),
    content            TEXT         NOT NULL CHECK (length(content) <= 8000),
    language           VARCHAR(5),
    sources            JSONB        NOT NULL DEFAULT '[]',
    tools_used         JSONB        NOT NULL DEFAULT '[]',
    uncertainty        VARCHAR(500),
    model              VARCHAR(60),
    input_tokens       INT,
    output_tokens      INT,
    cache_read_tokens  INT,
    latency_ms         INT,
    created_at         TIMESTAMPTZ  NOT NULL
);
CREATE INDEX ix_chat_messages_session ON chat_messages (session_id, created_at);
CREATE INDEX ix_chat_messages_user ON chat_messages (user_id, created_at);

CREATE TABLE knowledge_documents (
    id           UUID PRIMARY KEY,
    title        VARCHAR(300) NOT NULL,
    publisher    VARCHAR(200) NOT NULL,
    source_url   VARCHAR(500) NOT NULL,
    licence      VARCHAR(60)  NOT NULL,
    language     VARCHAR(5)   NOT NULL
        CONSTRAINT ck_knowledge_documents_language CHECK (language IN ('en', 'hi', 'te', 'kn', 'ta')),
    crops        TEXT[]       NOT NULL DEFAULT '{}',
    topics       TEXT[]       NOT NULL DEFAULT '{}',
    data_label   VARCHAR(30)  NOT NULL
        CONSTRAINT ck_knowledge_documents_label CHECK (data_label IN ('VERIFIED_SOURCE', 'SYNTHETIC_TEST_DATA')),
    sha256       VARCHAR(64)  NOT NULL CONSTRAINT uk_knowledge_documents_sha256 UNIQUE,
    status       VARCHAR(10)  NOT NULL
        CONSTRAINT ck_knowledge_documents_status CHECK (status IN ('ACTIVE', 'RETIRED')),
    chunk_count  INT          NOT NULL CHECK (chunk_count >= 0),
    ingested_at  TIMESTAMPTZ  NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 0
);

-- Spring AI PgVectorStore table (custom name, created here because initialize-schema=false).
-- metadata carries documentId, chunkIndex, language, crops, topics, licence, title, url, dataLabel.
CREATE TABLE knowledge_chunks (
    id         UUID PRIMARY KEY,
    content    TEXT         NOT NULL,
    metadata   JSON         NOT NULL,
    embedding  vector(384)  NOT NULL
);
CREATE INDEX ix_knowledge_chunks_hnsw ON knowledge_chunks USING hnsw (embedding vector_cosine_ops);

CREATE TABLE chat_usage_daily (
    user_id        UUID    NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    day            DATE    NOT NULL,
    requests       INT     NOT NULL DEFAULT 0,
    input_tokens   BIGINT  NOT NULL DEFAULT 0,
    output_tokens  BIGINT  NOT NULL DEFAULT 0,
    PRIMARY KEY (user_id, day)
);
