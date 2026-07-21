-- GitHub-style FA reviews (NIMBA-69, design §12.2): a reviewer batches
-- per-section comments in a draft review (pending, visible only to them) and
-- submits them with a verdict that drives the workflow transition. Threads are
-- rooted comments with replies; the DRI resolves them while correcting.
CREATE TABLE fa_review
(
    id             UUID PRIMARY KEY,
    credit_case_id UUID        NOT NULL,
    reviewer_id    UUID        NOT NULL,
    department     VARCHAR(20) NOT NULL,
    status         VARCHAR(20) NOT NULL,
    verdict        VARCHAR(30),
    summary        TEXT,
    created_at     TIMESTAMPTZ NOT NULL,
    submitted_at   TIMESTAMPTZ
);

CREATE INDEX idx_fa_review_case ON fa_review (credit_case_id);

CREATE TABLE fa_review_comment
(
    id                UUID PRIMARY KEY,
    credit_case_id    UUID        NOT NULL,
    section_key       VARCHAR(60) NOT NULL,
    review_id         UUID REFERENCES fa_review (id) ON DELETE SET NULL,
    parent_id         UUID REFERENCES fa_review_comment (id) ON DELETE CASCADE,
    author_id         UUID        NOT NULL,
    author_department VARCHAR(20) NOT NULL,
    body              TEXT        NOT NULL,
    pending           BOOLEAN     NOT NULL,
    resolved_at       TIMESTAMPTZ,
    resolved_by       UUID,
    created_at        TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_fa_review_comment_case ON fa_review_comment (credit_case_id, section_key);
