-- Amortization schedule, its lines, and the generated trades (NIMBA-13). All
-- monetary columns are exact NUMERIC, never floating point. The offset parameters
-- live on the schedule (adjustable per dossier, per the fiche métier). References
-- to the credit-case and the analyst are by id only; no FK crosses a module
-- boundary. The schedule -> line relationship is within one module, so it carries
-- a real FK with cascade delete.

CREATE TABLE amortization_schedule (
    id                     UUID         PRIMARY KEY,
    credit_case_id         UUID         NOT NULL,
    version_number         INT          NOT NULL,
    original_filename      VARCHAR(512) NOT NULL,
    uploaded_by            UUID         NOT NULL,
    uploaded_at            TIMESTAMPTZ  NOT NULL,
    ordinary_offset_months INT          NOT NULL,
    vr_offset_months       INT          NOT NULL,
    fixed_day_of_month     INT          NOT NULL,
    CONSTRAINT uq_amortization_schedule_case_version UNIQUE (credit_case_id, version_number)
);

CREATE INDEX idx_amortization_schedule_case ON amortization_schedule (credit_case_id);

CREATE TABLE amortization_schedule_line (
    id                 UUID          PRIMARY KEY,
    schedule_id        UUID          NOT NULL REFERENCES amortization_schedule (id) ON DELETE CASCADE,
    numero_echeance    VARCHAR(16)   NOT NULL,
    date_echeance      DATE,
    interet            NUMERIC(20, 4) NOT NULL,
    equipement         NUMERIC(20, 4) NOT NULL,
    assurance          NUMERIC(20, 4) NOT NULL,
    tracking           NUMERIC(20, 4) NOT NULL,
    immatriculation    NUMERIC(20, 4) NOT NULL,
    capital            NUMERIC(20, 4) NOT NULL,
    loyer_ht           NUMERIC(20, 4) NOT NULL,
    taxes              NUMERIC(20, 4) NOT NULL,
    loyer_ttc          NUMERIC(20, 4) NOT NULL,
    capital_restant_du NUMERIC(20, 4),
    CONSTRAINT uq_schedule_line_number UNIQUE (schedule_id, numero_echeance)
);

CREATE TABLE trade (
    id              UUID           PRIMARY KEY,
    credit_case_id  UUID           NOT NULL,
    schedule_id     UUID           NOT NULL,
    source_line_id  UUID           NOT NULL,
    numero_echeance VARCHAR(16)    NOT NULL,
    due_date        DATE           NOT NULL,
    amount          NUMERIC(20, 4) NOT NULL,
    amount_in_words VARCHAR(512)   NOT NULL,
    currency        VARCHAR(8)     NOT NULL,
    active          BOOLEAN        NOT NULL DEFAULT TRUE,
    generated_at    TIMESTAMPTZ    NOT NULL
);

CREATE INDEX idx_trade_case_active ON trade (credit_case_id, active);
