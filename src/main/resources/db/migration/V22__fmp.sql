-- Fiche de Mise en Place (NIMBA-60): a pure extract generated once a PV is
-- finalized. Only numero_pret and garantie_ref are new data — everything else
-- (identité, articulation, garanties, conditions de banque) is read live from
-- the associated PV's frozen snapshot each time, never duplicated here.
CREATE TABLE fmp
(
    id              UUID PRIMARY KEY,
    credit_case_id  UUID        NOT NULL UNIQUE REFERENCES credit_case (id),
    created_by      UUID        NOT NULL,
    numero_pret     VARCHAR(50) NOT NULL,
    garantie_ref    VARCHAR(100),
    created_at      TIMESTAMPTZ NOT NULL
);
