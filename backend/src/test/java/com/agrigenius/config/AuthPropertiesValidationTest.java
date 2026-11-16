package com.agrigenius.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.agrigenius.identity.config.JwtProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class AuthPropertiesValidationTest {

    private static final String VALID_SECRET = "YWdyaWdlbml1cy10ZXN0LW9ubHktc2lnbmluZy1rZXktTk9ULUEtU0VDUkVULTAw";

    @Configuration
    @EnableConfigurationProperties({JwtProperties.class, AppDatabaseRoleProperties.class})
    static class PropertiesConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfig.class)
            .withPropertyValues(
                    "agrigenius.database.app-role.manage=true",
                    "agrigenius.database.app-role.username=agrigenius_app",
                    "agrigenius.database.app-role.password=valid-app-role-password",
                    "agrigenius.security.jwt.issuer=agrigenius",
                    "agrigenius.security.jwt.audience=agrigenius-api",
                    "agrigenius.security.jwt.secret=" + VALID_SECRET,
                    "agrigenius.security.jwt.access-token-ttl=30m");

    @Test
    void validSettingsBind() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(JwtProperties.class).accessTokenTtl()).isEqualTo(Duration.ofMinutes(30));
            assertThat(context.getBean(JwtProperties.class).secretBytes()).hasSize(48);
        });
    }

    @Test
    void missingSecretIsRejected() {
        runner.withPropertyValues("agrigenius.security.jwt.secret=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void placeholderSecretIsRejected() {
        runner.withPropertyValues("agrigenius.security.jwt.secret=REPLACE_WITH_BASE64_OF_48_RANDOM_BYTES")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("base64"));
    }

    @Test
    void shortSecretIsRejected() {
        runner.withPropertyValues("agrigenius.security.jwt.secret=MDEyMzQ1Njc4OWFiY2RlZg==") // 16 bytes
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void ttlOutOfRangeIsRejected() {
        runner.withPropertyValues("agrigenius.security.jwt.access-token-ttl=0m")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("agrigenius.security.jwt.access-token-ttl=121m")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void invalidRoleUsernamesAreRejected() {
        runner.withPropertyValues("agrigenius.database.app-role.username=AgriApp")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("agrigenius.database.app-role.username=app';drop")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void weakOrUnsafeRolePasswordsAreRejected() {
        runner.withPropertyValues("agrigenius.database.app-role.password=short")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("agrigenius.database.app-role.password=has-a-quote'-in-it-xx")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void toStringRedactsSecrets() {
        JwtProperties jwt = new JwtProperties("agrigenius", "agrigenius-api", VALID_SECRET, Duration.ofMinutes(30));
        AppDatabaseRoleProperties role = new AppDatabaseRoleProperties(true, "agrigenius_app", "valid-app-role-password");

        assertThat(jwt.toString()).doesNotContain(VALID_SECRET).contains("<redacted>");
        assertThat(role.toString()).doesNotContain("valid-app-role-password").contains("<redacted>");
    }
}
