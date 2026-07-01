-- Audit trail (NIMBA-40). One row per state-changing request: actor, action,
-- method/path, outcome, correlation id and timestamp. Append-only in practice.
CREATE TABLE audit_event (
    id             UUID         PRIMARY KEY,
    occurred_at    TIMESTAMPTZ  NOT NULL,
    actor_id       UUID,
    actor_email    VARCHAR(320),
    action         VARCHAR(200) NOT NULL,
    method         VARCHAR(16)  NOT NULL,
    path           VARCHAR(512) NOT NULL,
    status         INT          NOT NULL,
    correlation_id VARCHAR(64)
);

CREATE INDEX idx_audit_event_occurred_at ON audit_event (occurred_at DESC);
