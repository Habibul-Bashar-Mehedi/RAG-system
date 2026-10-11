CREATE TABLE documents (
    id            UUID PRIMARY KEY,
    owner_id      VARCHAR(64)  NOT NULL,
    filename      VARCHAR(255) NOT NULL,
    content_type  VARCHAR(255),
    size_bytes    BIGINT       NOT NULL,
    status        VARCHAR(20)  NOT NULL,
    page_count    INTEGER      NOT NULL DEFAULT 0,
    chunk_count   INTEGER      NOT NULL DEFAULT 0,
    error_message TEXT,
    storage_path  VARCHAR(1024) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_documents_owner ON documents (owner_id);
CREATE INDEX idx_documents_status ON documents (status);

CREATE TABLE document_chunks (
    id           UUID PRIMARY KEY,
    document_id  UUID         NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    owner_id     VARCHAR(64)  NOT NULL,
    chunk_index  INTEGER      NOT NULL,
    page_number  INTEGER      NOT NULL,
    content      TEXT         NOT NULL,
    char_count   INTEGER      NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_chunks_document ON document_chunks (document_id);
CREATE INDEX idx_chunks_owner ON document_chunks (owner_id);

CREATE TABLE chat_sessions (
    id          UUID PRIMARY KEY,
    owner_id    VARCHAR(64)  NOT NULL,
    document_id UUID,
    title       VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL,
    updated_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_sessions_owner ON chat_sessions (owner_id);
CREATE INDEX idx_sessions_document ON chat_sessions (document_id);

CREATE TABLE chat_messages (
    id         UUID PRIMARY KEY,
    session_id UUID        NOT NULL REFERENCES chat_sessions (id) ON DELETE CASCADE,
    role       VARCHAR(16) NOT NULL,
    content    TEXT        NOT NULL,
    sources    TEXT,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_messages_session ON chat_messages (session_id);
