-- Client identity (NIMBA-55): descriptive client detail captured once on the
-- dossier and reused verbatim on the Fiche d'analyse, the PV and the FMP. Every
-- column is optional — the DRI fills these in incrementally while constituting
-- the dossier, they are never required to create it.
ALTER TABLE credit_case
    ADD COLUMN forme_juridique      VARCHAR(100),
    ADD COLUMN date_creation        DATE,
    ADD COLUMN adresse_physique     VARCHAR(300),
    ADD COLUMN activite_de_base     VARCHAR(300),
    ADD COLUMN code_nif             VARCHAR(50),
    ADD COLUMN principal_dirigeant  VARCHAR(200),
    ADD COLUMN date_entree_relation DATE,
    ADD COLUMN date_derniere_visite DATE,
    ADD COLUMN agence               VARCHAR(100),
    ADD COLUMN gestionnaire         VARCHAR(200),
    ADD COLUMN analyste             VARCHAR(200),
    ADD COLUMN cotation_precedente  VARCHAR(20),
    ADD COLUMN cotation_actuelle    VARCHAR(20);
