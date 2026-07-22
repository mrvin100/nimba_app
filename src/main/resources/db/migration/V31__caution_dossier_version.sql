-- A dossier is versioned: each amendment (a change to its shared content) bumps
-- the version, and the companion documents (Fiche, Notification) are re-issued
-- carrying that version. Existing dossiers start at version 1.
ALTER TABLE caution_dossier ADD COLUMN version INT NOT NULL DEFAULT 1;
