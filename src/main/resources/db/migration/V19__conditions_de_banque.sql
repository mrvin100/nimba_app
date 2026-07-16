-- Conditions de banque (NIMBA-57): bank-set financing terms captured once on the
-- dossier and reused on the Fiche d'analyse cover/§5, the PV and the FMP. Only
-- the terms the imported TA cannot derive live here — 1er loyer, loyer mensuel,
-- durée and valeur résiduelle stay computed from the schedule, never duplicated.
ALTER TABLE credit_case
    ADD COLUMN taux_interet_pct        NUMERIC(6, 3),
    ADD COLUMN frais_mise_en_place_pct NUMERIC(6, 3),
    ADD COLUMN com_engagement_pct      NUMERIC(6, 3),
    ADD COLUMN frais_etudes_pct        NUMERIC(6, 3),
    ADD COLUMN frais_divers            TEXT;
