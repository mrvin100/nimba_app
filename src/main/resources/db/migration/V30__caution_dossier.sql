-- Caution dossier (NIMBA-80): groups every document generated for one client
-- request against one appel d'offres (possibly several lots). A dossier de
-- caution de soumission always yields the two companion documents (Fiche
-- d'approbation + Notification de caution) plus whichever client-facing
-- documents (SMS/ACF/AFC) were requested. content_json holds the shared market
-- context (bénéficiaire, référence de l'appel d'offres, objet, lots, montants
-- par lot) reused across the dossier's documents and its companions.
CREATE TABLE caution_dossier (
    id                UUID         PRIMARY KEY,
    client_id         UUID         NOT NULL,
    reference_number  VARCHAR(64)  NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    content_json      TEXT         NOT NULL,
    created_by        UUID         NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_caution_dossier_reference_number UNIQUE (reference_number)
);

CREATE INDEX ix_caution_dossier_client_id ON caution_dossier (client_id);

-- A caution may belong to a dossier; null when created standalone, so every
-- document issued before dossiers existed stays valid.
ALTER TABLE caution ADD COLUMN dossier_id UUID;

CREATE INDEX ix_caution_dossier_id ON caution (dossier_id);
