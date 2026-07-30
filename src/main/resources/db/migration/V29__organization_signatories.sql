-- Standing signatories printed on generated legal documents (first consumer:
-- the Caution module's SMS/ACF) — configured once by an admin rather than
-- re-typed on every document, since they rarely change (NIMBA-77).
ALTER TABLE organization_settings
    ADD COLUMN signataire1_nom    VARCHAR(200),
    ADD COLUMN signataire1_titre  VARCHAR(200),
    ADD COLUMN signataire2_nom    VARCHAR(200),
    ADD COLUMN signataire2_titre  VARCHAR(200);
