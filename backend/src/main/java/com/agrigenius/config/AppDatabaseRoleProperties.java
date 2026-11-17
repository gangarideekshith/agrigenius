package com.agrigenius.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Least-privilege database role used by the running application ({@code DB_USERNAME}/{@code DB_PASSWORD}).
 *
 * <p>Flyway connects as the schema owner ({@code POSTGRES_USER}) and, after every migration,
 * the {@code afterMigrate.sql} callback creates or updates this role and grants it data-only
 * privileges (SELECT, INSERT, UPDATE, DELETE). The application itself cannot create, alter or
 * drop tables. Set {@code manage=false} where roles are provisioned outside the application.
 *
 * <p>The patterns keep both values safe to embed in the callback SQL.
 */
@Validated
@ConfigurationProperties(prefix = "agrigenius.database.app-role")
public record AppDatabaseRoleProperties(
        boolean manage,
        @NotBlank @Pattern(regexp = "[a-z_][a-z0-9_]{0,62}", message = "must be a lowercase PostgreSQL identifier")
        String username,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9+/=_.-]{16,128}",
                message = "must be 16-128 characters of letters, digits or + / = _ . - (generate one; see .env.example)")
        String password) {

    @Override
    public String toString() {
        return "AppDatabaseRoleProperties[manage=" + manage + ", username=" + username + ", password=<redacted>]";
    }
}
