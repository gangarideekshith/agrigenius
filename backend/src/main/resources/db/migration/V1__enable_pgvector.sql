-- Enable pgvector for future embedding/RAG storage.
-- Idempotent: the local Docker database already has the extension, so this is a no-op there.
-- No application tables are created until the Milestone 2 ERD is approved.
CREATE EXTENSION IF NOT EXISTS vector;
