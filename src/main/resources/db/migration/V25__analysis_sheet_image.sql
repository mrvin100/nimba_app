-- FA section images (NIMBA-63): uploaded figures embedded in the exported FA
-- (organigramme, figures du marché, facture proforma, liste des clients en
-- annexe). One row per file; the binary lives in MinIO under storage_key.
-- Scoped to the sheet + section key (not the section row, which is created
-- lazily and may not exist when the first image arrives).
CREATE TABLE analysis_sheet_image
(
    id                UUID PRIMARY KEY,
    analysis_sheet_id UUID         NOT NULL REFERENCES analysis_sheet (id) ON DELETE CASCADE,
    section_key       VARCHAR(60)  NOT NULL,
    file_name         VARCHAR(255) NOT NULL,
    content_type      VARCHAR(100) NOT NULL,
    size_bytes        BIGINT       NOT NULL,
    storage_key       VARCHAR(400) NOT NULL,
    caption           VARCHAR(300),
    uploaded_by       UUID         NOT NULL,
    uploaded_at       TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_analysis_sheet_image_sheet ON analysis_sheet_image (analysis_sheet_id, section_key);
