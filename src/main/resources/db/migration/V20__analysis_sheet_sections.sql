-- FA sections (NIMBA-58): replaces the sheet's single free-text content column
-- with one row per section key, matching the hardcoded typed-section framework
-- (docs/nimba-credit-workflow-design.md §10.2). No data migration needed — no FA
-- has been published against the old free-text field in any real environment yet.
CREATE TABLE analysis_sheet_section
(
    id                UUID PRIMARY KEY,
    analysis_sheet_id UUID         NOT NULL REFERENCES analysis_sheet (id) ON DELETE CASCADE,
    section_key       VARCHAR(60)  NOT NULL,
    content_json      TEXT,
    updated_at        TIMESTAMPTZ  NOT NULL,
    UNIQUE (analysis_sheet_id, section_key)
);

ALTER TABLE analysis_sheet
    DROP COLUMN content;
