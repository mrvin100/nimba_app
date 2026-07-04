-- Archiving a dossier (NIMBA-45) is an administrative act that hides it from the
-- day-to-day list without destroying anything: the timestamp doubles as the flag
-- (NULL = active). A status value would conflate the business lifecycle
-- (EN_ATTENTE_AMORTISSEMENT / TRADES_GENERES) with this cross-cutting concern.
ALTER TABLE credit_case ADD COLUMN archived_at TIMESTAMPTZ;

CREATE INDEX idx_credit_case_archived ON credit_case (archived_at);
