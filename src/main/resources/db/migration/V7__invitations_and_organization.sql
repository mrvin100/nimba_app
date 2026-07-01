-- Account provisioning (NIMBA-35). Accounts are created without a password and the
-- user sets it through a one-time invitation token (set-password link) delivered by
-- e-mail. Organisation settings hold the invitation e-mail sender identity.

-- Invited users have no password until they consume their invitation.
ALTER TABLE app_user ALTER COLUMN password_hash DROP NOT NULL;

-- One-time set-password tokens. Cascade-deleted with their user.
CREATE TABLE user_invitation (
    id          UUID         PRIMARY KEY,
    user_id     UUID         NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token       VARCHAR(255) NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ  NOT NULL,
    expires_at  TIMESTAMPTZ  NOT NULL,
    consumed_at TIMESTAMPTZ
);

CREATE INDEX idx_user_invitation_token ON user_invitation (token);
CREATE INDEX idx_user_invitation_user ON user_invitation (user_id);

-- Single-row organisation settings (id is always 1). Drives the sender identity of
-- invitation e-mails and the organisation name shown in the UI.
CREATE TABLE organization_settings (
    id                INT          PRIMARY KEY,
    organization_name VARCHAR(200) NOT NULL,
    sender_name       VARCHAR(200) NOT NULL,
    sender_email      VARCHAR(320) NOT NULL,
    updated_at        TIMESTAMPTZ  NOT NULL
);

INSERT INTO organization_settings (id, organization_name, sender_name, sender_email, updated_at)
VALUES (1, 'Nimba', 'Nimba', 'no-reply@nimba.local', now());
