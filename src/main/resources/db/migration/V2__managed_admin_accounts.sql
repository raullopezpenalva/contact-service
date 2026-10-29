CREATE TABLE admin_accounts (
    id uuid PRIMARY KEY,
    login varchar(64) NOT NULL UNIQUE,
    password_hash varchar(255) NOT NULL,
    enabled boolean NOT NULL,
    authentication_version bigint NOT NULL,
    row_version bigint NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT admin_login_canonical CHECK (login ~ '^[a-z0-9][a-z0-9._-]{2,63}$'),
    CONSTRAINT admin_hash_encoded CHECK (password_hash LIKE '$argon2id$%'),
    CONSTRAINT admin_auth_version_nonnegative CHECK (authentication_version >= 0)
);
CREATE TABLE admin_account_roles (
    account_id uuid NOT NULL REFERENCES admin_accounts(id),
    role varchar(32) NOT NULL CHECK (role IN ('ADMIN')),
    PRIMARY KEY (account_id,role)
);
-- A shared row lock serializes operator changes, including first provision and
-- concurrent attempts to disable the last usable administrator.
CREATE TABLE admin_management_lock (id integer PRIMARY KEY CHECK (id=1));
INSERT INTO admin_management_lock(id) VALUES (1);
-- Schema for #45/#46. No tokens/sessions are issued by the #44 implementation.
CREATE TABLE admin_sessions (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES admin_accounts(id),
    authentication_version bigint NOT NULL CHECK (authentication_version >= 0),
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    revoked_at timestamp(6) with time zone,
    CONSTRAINT admin_session_expiry CHECK (expires_at > created_at)
);
CREATE INDEX admin_sessions_account_idx ON admin_sessions(account_id);
CREATE INDEX admin_sessions_expiry_idx ON admin_sessions(expires_at);
