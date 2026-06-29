-- Minimal client credit case (NIMBA-10): just enough to attach an amortization
-- schedule and its trades. The case number is human-readable, sequential and
-- unique. created_by references the DRI analyst (app_user) who opened it; no FK
-- is declared across the module boundary, the relationship is enforced in the
-- application layer through the identity module's API.
CREATE TABLE credit_case (
    id           UUID         PRIMARY KEY,
    case_number  VARCHAR(32)  NOT NULL,
    client_name  VARCHAR(200) NOT NULL,
    product_type VARCHAR(64)  NOT NULL,
    currency     VARCHAR(8)   NOT NULL,
    created_by   UUID         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL,
    updated_at   TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_credit_case_number UNIQUE (case_number)
);

-- Per-year counter backing the DOS-{year}-{NNNN} sequence. The generator upserts
-- this row atomically (INSERT ... ON CONFLICT ... RETURNING), so concurrent case
-- creations get strictly increasing numbers without a race.
CREATE TABLE credit_case_counter (
    year       INT PRIMARY KEY,
    last_value INT NOT NULL
);
