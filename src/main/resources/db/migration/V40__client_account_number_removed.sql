-- The account number no longer lives on the client record: a client can hold
-- several credits or cautions against different accounts, so it now belongs
-- to the credit case (already the case, see V11) or the caution dossier's
-- content (the "numeroCompte" common field, see CautionFieldRegistry).

ALTER TABLE client
    DROP COLUMN account_number;
