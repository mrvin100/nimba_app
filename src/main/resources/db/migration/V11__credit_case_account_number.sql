-- The account number printed on a traité is the client's account at the bank,
-- so it belongs to the credit case (per dossier), not to deployment
-- configuration. Nullable: existing cases fall back to the configured
-- nimba.traite.account-number until edited.
alter table credit_case
    add column account_number varchar(50);
