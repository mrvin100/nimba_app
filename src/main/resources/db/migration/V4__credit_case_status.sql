-- Phase-1 case status (NIMBA-12): reflects only whether trades have been generated
-- yet. Additive, non-destructive column with a default so existing rows are valid.
ALTER TABLE credit_case
    ADD COLUMN status VARCHAR(64) NOT NULL DEFAULT 'EN_ATTENTE_AMORTISSEMENT';
