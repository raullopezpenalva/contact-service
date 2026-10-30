-- Unbound sessions predate token issuance and cannot authenticate after migration.
ALTER TABLE admin_sessions ADD COLUMN signing_key_id varchar(64);
ALTER TABLE admin_sessions ADD COLUMN token_id uuid;
UPDATE admin_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE revoked_at IS NULL;
ALTER TABLE admin_sessions ADD CONSTRAINT admin_sessions_key_id
    CHECK (signing_key_id IS NULL OR signing_key_id ~ '^[A-Za-z0-9_-]{1,64}$');
CREATE UNIQUE INDEX admin_sessions_token_id_idx ON admin_sessions(token_id);
CREATE INDEX admin_sessions_signing_key_idx ON admin_sessions(signing_key_id);
