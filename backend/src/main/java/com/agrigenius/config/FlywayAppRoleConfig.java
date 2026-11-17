package com.agrigenius.config;

import java.util.Map;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Passes the application role to Flyway's {@code afterMigrate.sql} callback as placeholders,
 * so credentials never appear in migration files or in Spring property placeholders.
 */
@Configuration
public class FlywayAppRoleConfig {

    @Bean
    FlywayConfigurationCustomizer appRolePlaceholders(AppDatabaseRoleProperties appRole) {
        return configuration -> configuration.placeholders(Map.of(
                "manageAppRole", Boolean.toString(appRole.manage()),
                "appDbUser", appRole.username(),
                "appDbPassword", appRole.password()));
    }
}
