-- The case number now carries the product (LEA-2026-0001, MC2-2026-0007)
-- instead of the generic DOS-2026-0001, so the counter must be kept per
-- product as well as per year. Already-issued case numbers are untouched
-- (case_number is a stored string, never recomputed) and use the "DOS"
-- prefix, textually distinct from the new per-product codes, so there is no
-- collision risk in simply resetting the counter state.
DROP TABLE credit_case_counter;

CREATE TABLE credit_case_counter (
    year         INT NOT NULL,
    product_type VARCHAR(20) NOT NULL,
    last_value   INT NOT NULL,
    PRIMARY KEY (year, product_type)
);
