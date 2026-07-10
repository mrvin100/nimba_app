-- Fiche d'analyse (NIMBA-48): one per credit case, drafted by the DRI once the TA
-- is uploaded, published to move the dossier into DCM's review. content is a
-- placeholder free-text field until the real per-variant FA structure is supplied
-- (see docs/nimba-credit-workflow-design.md) — the fa_variant it was drafted under
-- is fixed at creation, matching the case's type at that time.
CREATE TABLE analysis_sheet (
    id              UUID         PRIMARY KEY,
    credit_case_id  UUID         NOT NULL,
    fa_variant      VARCHAR(32)  NOT NULL,
    status          VARCHAR(16)  NOT NULL,
    content         TEXT,
    created_by      UUID         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    updated_at      TIMESTAMPTZ  NOT NULL,
    published_at    TIMESTAMPTZ,
    CONSTRAINT uq_analysis_sheet_case UNIQUE (credit_case_id)
);
