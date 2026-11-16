-- Flyway callback: runs as the schema owner after every successful migrate.
-- Creates or updates the least-privilege application role and grants it data-only access,
-- so the running backend can read and write rows but cannot create, alter or drop objects.
-- Placeholders are supplied by FlywayAppRoleConfig and validated by AppDatabaseRoleProperties
-- (identifier / password character sets), which keeps them safe to embed here.
DO $$
BEGIN
    IF '${manageAppRole}' <> 'true' THEN
        RETURN;
    END IF;
    IF '${appDbUser}' = current_user THEN
        RAISE EXCEPTION 'Application role must differ from the Flyway owner role';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '${appDbUser}') THEN
        EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE',
                       '${appDbUser}', '${appDbPassword}');
    ELSE
        EXECUTE format('ALTER ROLE %I LOGIN PASSWORD %L NOSUPERUSER NOCREATEDB NOCREATEROLE',
                       '${appDbUser}', '${appDbPassword}');
    END IF;

    EXECUTE format('GRANT CONNECT ON DATABASE %I TO %I', current_database(), '${appDbUser}');
    EXECUTE format('GRANT USAGE ON SCHEMA public TO %I', '${appDbUser}');
    EXECUTE format('REVOKE CREATE ON SCHEMA public FROM %I', '${appDbUser}');
    EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO %I', '${appDbUser}');
    EXECUTE format('GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO %I', '${appDbUser}');
    -- Migration history is owned by Flyway; the application only needs to read it (for health/validation).
    EXECUTE format('REVOKE INSERT, UPDATE, DELETE ON flyway_schema_history FROM %I', '${appDbUser}');
END
$$;
