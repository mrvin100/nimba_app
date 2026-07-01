-- User avatars (NIMBA-39). The image bytes live in object storage (MinIO); the row
-- keeps only the object key. Null means no avatar (the UI falls back to initials).
ALTER TABLE app_user ADD COLUMN avatar_key VARCHAR(255);
