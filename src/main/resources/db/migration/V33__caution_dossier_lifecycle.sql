-- Dossier lifecycle (NIMBA-82, Tranche 2): the dossier-level lock replaces the
-- earlier OPEN/CLOSED status. A dossier is BROUILLON (editable), FINALISE
-- (frozen) or EN_PROROGATION (temporarily reopened by a manager). Existing
-- values are remapped: OPEN -> BROUILLON, CLOSED -> FINALISE (a closed dossier
-- was a served, locked request).
UPDATE caution_dossier SET status = 'BROUILLON' WHERE status = 'OPEN';
UPDATE caution_dossier SET status = 'FINALISE'  WHERE status = 'CLOSED';

-- The dossier's lifecycle journal: every finalize / proroge / refinalize, with
-- the responsible and (for a prorogation) the required reason.
CREATE TABLE caution_dossier_event (
    id          UUID         PRIMARY KEY,
    dossier_id  UUID         NOT NULL,
    action      VARCHAR(32)  NOT NULL,
    from_status VARCHAR(32)  NOT NULL,
    to_status   VARCHAR(32)  NOT NULL,
    reason      TEXT,
    actor       UUID         NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX ix_caution_dossier_event_dossier_id ON caution_dossier_event (dossier_id);
