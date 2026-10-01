CREATE TABLE users
(
    id                    BIGSERIAL PRIMARY KEY,
    email                 VARCHAR(255) NOT NULL,
    password_hash         VARCHAR(100),
    provider              VARCHAR(20)  NOT NULL,
    provider_id           VARCHAR(255),
    nickname              VARCHAR(20)  NOT NULL,
    role                  VARCHAR(20)  NOT NULL,
    failed_login_count    INTEGER      NOT NULL DEFAULT 0,
    first_failed_login_at TIMESTAMPTZ,
    locked_until          TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL,
    updated_at            TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_provider_id UNIQUE (provider, provider_id),
    CONSTRAINT ck_users_role CHECK (role IN ('VIEWER', 'CREATOR', 'ADMIN')),
    -- 이메일 회원은 비밀번호만, OAuth 회원은 제공자 사용자 ID만 갖는다.
    CONSTRAINT ck_users_credentials CHECK (
        (provider = 'LOCAL' AND password_hash IS NOT NULL AND provider_id IS NULL)
            OR (provider <> 'LOCAL' AND password_hash IS NULL AND provider_id IS NOT NULL))
);

CREATE TABLE refresh_tokens
(
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id),
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_refresh_tokens_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_refresh_tokens_user_id ON refresh_tokens (user_id);
