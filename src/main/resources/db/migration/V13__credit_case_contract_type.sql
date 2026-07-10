-- NIMBA-46: dossier type refinement. product_type gains MC2_MUFFA (application-side
-- enum, no DB change needed for that). contract_type only applies to LEASING cases
-- (AVEC_CONTRAT / SANS_CONTRAT) — nullable because every other product leaves it unset.
ALTER TABLE credit_case ADD COLUMN contract_type VARCHAR(32);
