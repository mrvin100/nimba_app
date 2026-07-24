-- Caution module (NIMBA-76): generated banking documents (Caution de
-- Soumission/SMS, Attestation de Capacité Financière/ACF, later Avance sur
-- Démarrage/AVD...), keyed to a client (not a credit_case) so a company that
-- never has a leasing dossier can still request one. content_json holds
-- every shared + document-type-specific field answer, keyed by
-- CautionFieldDefinition.key. The snap_* columns freeze the issuing client's
-- identity at finalization (all null while DRAFT), same pattern as pv's own
-- snapshot embeddable.
CREATE TABLE caution (
    id                UUID         PRIMARY KEY,
    client_id         UUID         NOT NULL,
    document_type     VARCHAR(32)  NOT NULL,
    reference_number  VARCHAR(64)  NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    content_json      TEXT         NOT NULL,
    created_by        UUID         NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    finalized_at      TIMESTAMPTZ,
    snap_matricule        VARCHAR(50),
    snap_raison_sociale   VARCHAR(200),
    snap_sigle            VARCHAR(100),
    snap_adresse_physique VARCHAR(300),
    snap_rccm             VARCHAR(50),
    snap_account_number   VARCHAR(50),
    snap_agence           VARCHAR(100),
    CONSTRAINT uq_caution_reference_number UNIQUE (reference_number)
);

CREATE INDEX ix_caution_client_id ON caution (client_id);

-- Singleton counter backing the global sequential reference number, shared by
-- every document type (an atomic upsert, same technique as
-- credit_case_counter — see CautionNumberGenerator).
CREATE TABLE caution_counter (
    id         INT PRIMARY KEY,
    last_value INT NOT NULL
);
