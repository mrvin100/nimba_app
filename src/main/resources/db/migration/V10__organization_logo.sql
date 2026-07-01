-- Organisation logo. The image bytes live in object storage (MinIO); the settings
-- row keeps only the object key and its content type (for serving). Null means no
-- logo configured (documents and the login screen fall back to the name alone).
ALTER TABLE organization_settings ADD COLUMN logo_key VARCHAR(255);
ALTER TABLE organization_settings ADD COLUMN logo_content_type VARCHAR(100);
