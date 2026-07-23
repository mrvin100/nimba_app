-- Caution foundation (NIMBA-82): the dossier becomes the aggregate root and
-- every document is attached to one. Two changes:
--   1. Optimistic-lock columns so a concurrent edit fails cleanly (409) instead
--      of silently overwriting (JPA @Version).
--   2. A one-off data migration that attaches every legacy document created
--      before dossiers existed (dossier_id IS NULL) to a generated "legacy"
--      dossier — one document to one dossier — carrying the document's content
--      as the dossier's common content, so nothing is lost and every document
--      becomes reachable through the new dossier-centric flow.

ALTER TABLE caution_dossier ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE caution         ADD COLUMN lock_version BIGINT NOT NULL DEFAULT 0;

-- A data-modifying CTE: create one legacy dossier per orphan document, then
-- link each document to its dossier. caution.reference_number is unique, so the
-- 'LEGACY-' || reference_number key maps one-to-one.
WITH new_dossiers AS (
    INSERT INTO caution_dossier (
        id, client_id, reference_number, status, version, content_json, created_by, created_at, updated_at
    )
    SELECT
        gen_random_uuid(),
        c.client_id,
        'LEGACY-' || c.reference_number,
        'OPEN',
        1,
        c.content_json,
        c.created_by,
        c.created_at,
        c.updated_at
    FROM caution c
    WHERE c.dossier_id IS NULL
    RETURNING id, reference_number
)
UPDATE caution c
SET dossier_id = nd.id
FROM new_dossiers nd
WHERE nd.reference_number = 'LEGACY-' || c.reference_number
  AND c.dossier_id IS NULL;
