-- Dossier guarantees (NIMBA-56): held by the bank already, or still to be obtained,
-- each with 0..n proof files. References the credit case by id only; no FK crosses
-- the module boundary. attachment -> guarantee is within one module, so it carries
-- a real FK with cascade delete.

CREATE TABLE guarantee (
    id             UUID        PRIMARY KEY,
    credit_case_id UUID        NOT NULL,
    kind           VARCHAR(20) NOT NULL,
    description    TEXT        NOT NULL,
    created_by     UUID        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_guarantee_case ON guarantee (credit_case_id);

CREATE TABLE guarantee_attachment (
    id           UUID         PRIMARY KEY,
    guarantee_id UUID         NOT NULL REFERENCES guarantee (id) ON DELETE CASCADE,
    file_name    VARCHAR(300) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    size_bytes   BIGINT       NOT NULL,
    storage_key  VARCHAR(400) NOT NULL,
    uploaded_by  UUID         NOT NULL,
    uploaded_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_guarantee_attachment_guarantee ON guarantee_attachment (guarantee_id);
