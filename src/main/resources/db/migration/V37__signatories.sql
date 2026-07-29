-- Replaces the two frozen organization_settings signatory slots with a proper
-- Signatory entity: any number of standing signatories, each either a real user
-- profile (resolved live, never duplicated) or a standalone record for someone
-- without an account, each restrictable to specific direction+role holders.

ALTER TABLE app_user
    ADD COLUMN titre VARCHAR(200),
    ADD COLUMN signatory_opt_in BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE signatory (
    id              UUID PRIMARY KEY,
    nom             VARCHAR(200) NOT NULL,
    titre           VARCHAR(200) NOT NULL,
    category        VARCHAR(20) NOT NULL,
    creation_reason VARCHAR(500) NOT NULL,
    created_by      UUID,
    created_at      TIMESTAMPTZ NOT NULL
);

CREATE TABLE signatory_authorization (
    signatory_id     UUID NOT NULL REFERENCES signatory (id) ON DELETE CASCADE,
    department       VARCHAR(20) NOT NULL,
    department_role  VARCHAR(20) NOT NULL,
    PRIMARY KEY (signatory_id, department, department_role)
);

-- Preserve any signatory an admin already configured, as a standalone record
-- with no authorization rows (global, matching the previous org-wide behavior).
INSERT INTO signatory (id, nom, titre, category, creation_reason, created_by, created_at)
SELECT gen_random_uuid(), signataire1_nom, COALESCE(signataire1_titre, 'RAS'), 'INTERNE',
       'Migre depuis les parametres de l''organisation', NULL, now()
FROM organization_settings
WHERE signataire1_nom IS NOT NULL;

INSERT INTO signatory (id, nom, titre, category, creation_reason, created_by, created_at)
SELECT gen_random_uuid(), signataire2_nom, COALESCE(signataire2_titre, 'RAS'), 'INTERNE',
       'Migre depuis les parametres de l''organisation', NULL, now()
FROM organization_settings
WHERE signataire2_nom IS NOT NULL;

ALTER TABLE organization_settings
    DROP COLUMN signataire1_nom,
    DROP COLUMN signataire1_titre,
    DROP COLUMN signataire2_nom,
    DROP COLUMN signataire2_titre;
