-- Client unification, part 2 (NIMBA-83): a credit case now references a Client (the
-- single source of client identity) instead of carrying an embedded copy. Existing
-- dossiers are migrated to Client rows, deduplicated by code_nif when present (the
-- tax id is a reliable natural key), otherwise one client per dossier.

ALTER TABLE credit_case ADD COLUMN client_id UUID;

-- Group dossiers: by code_nif when set and non-blank, else by the dossier's own id
-- (which forces a 1:1 client). A temp table holds one fresh client id per group, so
-- the grouping is computed once and the links below are unambiguous.
CREATE TEMP TABLE cc_client_map (group_key TEXT PRIMARY KEY, client_id UUID);

INSERT INTO cc_client_map (group_key, client_id)
SELECT grp.group_key, gen_random_uuid()
FROM (SELECT DISTINCT coalesce(nullif(btrim(code_nif), ''), id::text) AS group_key FROM credit_case) grp;

-- Create one client per group, from that group's earliest dossier as the representative.
INSERT INTO client (
    id, matricule, type, raison_sociale, forme_juridique, date_creation, adresse_physique,
    activite_de_base, code_nif, account_number, principal_dirigeant, date_entree_relation,
    date_derniere_visite, agence, gestionnaire, analyste, cotation_precedente,
    cotation_actuelle, created_by, created_at, updated_at
)
SELECT m.client_id, NULL, 'ENTREPRISE', rep.client_name, rep.forme_juridique, rep.date_creation,
       rep.adresse_physique, rep.activite_de_base, rep.code_nif, rep.account_number,
       rep.principal_dirigeant, rep.date_entree_relation, rep.date_derniere_visite, rep.agence,
       rep.gestionnaire, rep.analyste, rep.cotation_precedente, rep.cotation_actuelle,
       rep.created_by, rep.created_at, rep.updated_at
FROM cc_client_map m
JOIN LATERAL (
    SELECT c.* FROM credit_case c
    WHERE coalesce(nullif(btrim(c.code_nif), ''), c.id::text) = m.group_key
    ORDER BY c.created_at, c.id
    LIMIT 1
) rep ON true;

-- Link every dossier to its group's client.
UPDATE credit_case cc
SET client_id = m.client_id
FROM cc_client_map m
WHERE coalesce(nullif(btrim(cc.code_nif), ''), cc.id::text) = m.group_key;

DROP TABLE cc_client_map;

ALTER TABLE credit_case ALTER COLUMN client_id SET NOT NULL;

-- Drop the now-migrated free-text name and embedded identity columns; the client
-- record is the sole home for these from now on. account_number stays on the case
-- (it backs traité rendering) and is also copied onto the client above.
ALTER TABLE credit_case
    DROP COLUMN client_name,
    DROP COLUMN forme_juridique,
    DROP COLUMN date_creation,
    DROP COLUMN adresse_physique,
    DROP COLUMN activite_de_base,
    DROP COLUMN code_nif,
    DROP COLUMN principal_dirigeant,
    DROP COLUMN date_entree_relation,
    DROP COLUMN date_derniere_visite,
    DROP COLUMN agence,
    DROP COLUMN gestionnaire,
    DROP COLUMN analyste,
    DROP COLUMN cotation_precedente,
    DROP COLUMN cotation_actuelle;
