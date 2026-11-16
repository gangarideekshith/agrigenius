-- Identity and farmer profile tables (Milestone 2, ADR-0003).
-- Ids are random UUIDs so account ids cannot be enumerated.

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    mobile_number   VARCHAR(13)  NOT NULL,           -- normalised +91XXXXXXXXXX
    email           VARCHAR(254),                    -- optional, stored lowercase
    password_hash   VARCHAR(100) NOT NULL,           -- {bcrypt}$2a$12$...
    role            VARCHAR(30)  NOT NULL
        CONSTRAINT ck_users_role CHECK (role IN ('FARMER', 'ADMIN', 'AGRICULTURAL_EXPERT', 'BUYER', 'VENDOR')),
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
        CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    last_login_at   TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version         BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_users_mobile_number UNIQUE (mobile_number),
    CONSTRAINT ck_users_mobile_format CHECK (mobile_number ~ '^\+91[6-9][0-9]{9}$'),
    CONSTRAINT ck_users_email_lowercase CHECK (email IS NULL OR email = lower(email))
);

CREATE UNIQUE INDEX uk_users_email ON users (email) WHERE email IS NOT NULL;

CREATE TABLE farmer_profiles (
    user_id             UUID PRIMARY KEY
        CONSTRAINT fk_farmer_profiles_user REFERENCES users (id) ON DELETE CASCADE,
    full_name           VARCHAR(100) NOT NULL,
    preferred_language  VARCHAR(5)   NOT NULL
        CONSTRAINT ck_farmer_profiles_language CHECK (preferred_language IN ('en', 'hi', 'te', 'kn', 'ta')),
    state               VARCHAR(60),
    district            VARCHAR(60),
    village             VARCHAR(100),
    address_line        VARCHAR(200),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version             BIGINT       NOT NULL DEFAULT 0
);
