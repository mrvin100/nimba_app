-- PV structural correction (NIMBA-61): points forts/faibles are no longer typed
-- directly on the PV — they are read from the FA's own points forts/faibles
-- sections and frozen into the snapshot at finalization, like every other
-- BOUND section. Also adds the conditions de banque's residual-value % to the
-- snapshot, mirroring V23's addition on credit_case.
ALTER TABLE pv
    DROP COLUMN points_forts,
    DROP COLUMN points_faibles,
    ADD COLUMN snap_points_forts        TEXT,
    ADD COLUMN snap_points_faibles      TEXT,
    ADD COLUMN snap_valeur_residuelle_pct NUMERIC(6, 3);
