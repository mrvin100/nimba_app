-- PV de Comité de Crédit (NIMBA-59): modeled per dossier, séance-dated. Drafted
-- by the DCM once the comité has approved the dossier, then finalized —
-- immutable from then on, freezing a snapshot of the dossier's identité,
-- articulation, garanties and conditions de banque (design §10.3).
CREATE TABLE pv
(
    id                            UUID PRIMARY KEY,
    credit_case_id                UUID         NOT NULL UNIQUE REFERENCES credit_case (id),
    created_by                    UUID         NOT NULL,
    seance_date                   DATE         NOT NULL,
    status                        VARCHAR(20)  NOT NULL,
    rapporteur                    VARCHAR(200),
    president                     VARCHAR(200),
    points_forts                  TEXT,
    points_faibles                TEXT,
    created_at                    TIMESTAMPTZ  NOT NULL,
    updated_at                    TIMESTAMPTZ  NOT NULL,
    finalized_at                  TIMESTAMPTZ,
    -- Frozen at finalization; null while DRAFT (see PvIdentitySnapshot).
    snap_forme_juridique          VARCHAR(100),
    snap_date_creation            DATE,
    snap_adresse_physique         VARCHAR(300),
    snap_activite_de_base         VARCHAR(300),
    snap_code_nif                 VARCHAR(50),
    snap_principal_dirigeant      VARCHAR(200),
    snap_date_entree_relation     DATE,
    snap_date_derniere_visite     DATE,
    snap_agence                   VARCHAR(100),
    snap_gestionnaire             VARCHAR(200),
    snap_analyste                 VARCHAR(200),
    snap_cotation_precedente      VARCHAR(20),
    snap_cotation_actuelle        VARCHAR(20),
    -- Frozen at finalization; null while DRAFT (see PvArticulationSnapshot).
    snap_loan_amount               NUMERIC(20, 4),
    snap_duration_months           INTEGER,
    snap_total_equipement          NUMERIC(20, 4),
    snap_total_assurance           NUMERIC(20, 4),
    snap_total_tracking            NUMERIC(20, 4),
    snap_total_immatriculation     NUMERIC(20, 4),
    snap_total_interet              NUMERIC(20, 4),
    snap_premier_loyer_ttc          NUMERIC(20, 4),
    snap_loyer_mensuel_ht           NUMERIC(20, 4),
    snap_valeur_residuelle          NUMERIC(20, 4),
    -- Frozen at finalization; null while DRAFT (see PvConditionsSnapshot).
    snap_taux_interet_pct          NUMERIC(6, 3),
    snap_frais_mise_en_place_pct   NUMERIC(6, 3),
    snap_com_engagement_pct        NUMERIC(6, 3),
    snap_frais_etudes_pct          NUMERIC(6, 3),
    snap_frais_divers              TEXT
);

-- Débats du comité: typed by the DCM when drafting the PV, replaced wholesale on
-- every draft edit (ordre preserves entry order — the id is a random UUID).
CREATE TABLE pv_debat
(
    id             UUID PRIMARY KEY,
    pv_id          UUID        NOT NULL REFERENCES pv (id) ON DELETE CASCADE,
    preoccupation  TEXT        NOT NULL,
    reponse        TEXT        NOT NULL,
    recommandation TEXT        NOT NULL,
    ordre          INTEGER     NOT NULL
);

-- One row per guarantee, written once at finalization (never updated after).
CREATE TABLE pv_guarantee_snapshot
(
    id          UUID PRIMARY KEY,
    pv_id       UUID        NOT NULL REFERENCES pv (id) ON DELETE CASCADE,
    kind        VARCHAR(20) NOT NULL,
    description TEXT        NOT NULL
);
