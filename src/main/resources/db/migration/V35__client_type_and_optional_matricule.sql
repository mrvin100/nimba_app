-- Client unification, part 1 (NIMBA-83): the client registry becomes the single
-- source of client identity, shared by the Caution module and (from V36) the
-- credit-case module.
--
-- 1) A client now has a type. Every product is corporate today, so existing rows
--    default to ENTREPRISE; PARTICULIER/AUTRE are reserved for a later product line.
ALTER TABLE client ADD COLUMN type VARCHAR(32) NOT NULL DEFAULT 'ENTREPRISE';

-- 2) matricule (the bank's internal code) is no longer mandatory — a client created
--    from a migrated leasing dossier has none yet. It stays unique when present, so
--    the total unique constraint becomes a partial unique index (many NULLs allowed).
ALTER TABLE client ALTER COLUMN matricule DROP NOT NULL;
ALTER TABLE client DROP CONSTRAINT uq_client_matricule;
CREATE UNIQUE INDEX uq_client_matricule ON client (matricule) WHERE matricule IS NOT NULL;
