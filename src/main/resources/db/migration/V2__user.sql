-- DRI analyst identity (NIMBA-8). The email is the login identifier and is unique
-- across the table. The password is stored only as a BCrypt hash. Single role in
-- this phase (DRI_ANALYST); no multi-role permission system. Table is named
-- `app_user` because `user` is a reserved word in PostgreSQL.
CREATE TABLE app_user (
    id            UUID         PRIMARY KEY,
    full_name     VARCHAR(255) NOT NULL,
    email         VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(64)  NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_app_user_email UNIQUE (email)
);
