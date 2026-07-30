-- Per-document history (NIMBA-82, Tranche 3): every edit of a document records
-- an immutable version capturing the content before and after, who made the
-- change, when, and (optionally) why. This is the granular audit trail the
-- refonte requires — a single document among several can be traced without
-- touching the others.
CREATE TABLE caution_document_version (
    id              UUID         PRIMARY KEY,
    document_id     UUID         NOT NULL,
    content_before  TEXT         NOT NULL,
    content_after   TEXT         NOT NULL,
    reason          TEXT,
    actor           UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_caution_document_version_document_id ON caution_document_version (document_id);
