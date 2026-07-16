-- Conditions de banque (NIMBA-61): valeur résiduelle % is a bank-set contractual
-- term (e.g. "2%"), distinct from the TA-derived VR amount already computed in
-- the articulation — a real document analysis pass caught this missing field.
ALTER TABLE credit_case
    ADD COLUMN valeur_residuelle_pct NUMERIC(6, 3);
