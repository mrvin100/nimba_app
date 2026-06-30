-- Multi-direction RBAC (NIMBA-31). A user gains an account status and an optional
-- global admin flag, and its single role is replaced by a set of (direction, role)
-- memberships. Existing users are migrated to a DRI MEMBER membership, then the old
-- single-role column is dropped.

ALTER TABLE app_user ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE app_user ADD COLUMN platform_admin BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE user_membership (
    user_id    UUID        NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    department VARCHAR(32) NOT NULL,
    role       VARCHAR(32) NOT NULL,
    CONSTRAINT pk_user_membership PRIMARY KEY (user_id, department)
);

-- Migrate the previous single DRI analyst role into a membership.
INSERT INTO user_membership (user_id, department, role)
SELECT id, 'DRI', 'MEMBER' FROM app_user;

ALTER TABLE app_user DROP COLUMN role;
