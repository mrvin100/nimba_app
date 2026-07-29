-- Signatory civility (Monsieur/Madame), confirmed once on the profile or the
-- standalone record instead of retyped by hand every time a dossier names a
-- signatory: the dossier form now derives it automatically from whichever
-- candidate the caller picks.

ALTER TABLE app_user
    ADD COLUMN civility VARCHAR(10);

ALTER TABLE signatory
    ADD COLUMN civility VARCHAR(10) NOT NULL DEFAULT 'MONSIEUR';

ALTER TABLE signatory
    ALTER COLUMN civility DROP DEFAULT;
