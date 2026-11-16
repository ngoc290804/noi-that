-- Accounts and persistent sessions. No catalog data or prices are changed.
CREATE TABLE luma.admin_accounts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    username varchar(64) NOT NULL UNIQUE CHECK (username ~ '^[a-z0-9][a-z0-9._-]{2,63}$'),
    display_name varchar(100) NOT NULL,
    password_hash varchar(100) NOT NULL,
    role varchar(20) NOT NULL CHECK (role IN ('ADMIN','EDITOR')),
    active boolean NOT NULL DEFAULT true,
    must_change_password boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    last_login_at timestamptz,
    deleted_at timestamptz
);
CREATE TRIGGER admin_accounts_write_version BEFORE UPDATE ON luma.admin_accounts
FOR EACH ROW EXECUTE FUNCTION luma.set_write_version();

CREATE TABLE luma.spring_session (
    primary_id char(36) NOT NULL PRIMARY KEY,
    session_id char(36) NOT NULL,
    creation_time bigint NOT NULL,
    last_access_time bigint NOT NULL,
    max_inactive_interval integer NOT NULL,
    expiry_time bigint NOT NULL,
    principal_name varchar(100)
);
CREATE UNIQUE INDEX spring_session_id_idx ON luma.spring_session(session_id);
CREATE INDEX spring_session_expiry_idx ON luma.spring_session(expiry_time);
CREATE INDEX spring_session_principal_idx ON luma.spring_session(principal_name);
CREATE TABLE luma.spring_session_attributes (
    session_primary_id char(36) NOT NULL REFERENCES luma.spring_session(primary_id) ON DELETE CASCADE,
    attribute_name varchar(200) NOT NULL,
    attribute_bytes bytea NOT NULL,
    PRIMARY KEY(session_primary_id,attribute_name)
);
CREATE TABLE luma.auth_login_attempts (
    attempt_key varchar(64) PRIMARY KEY,
    attempts integer NOT NULL,
    window_started_at timestamptz NOT NULL DEFAULT now()
);
-- The first admin is created by its owner via the protected one-time setup flow.
-- Never seed a shared/default password in source or migrations.
