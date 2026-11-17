package com.agrigenius.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.agrigenius.integration.ml.MlServiceProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/** Bad configuration must stop startup with a clear error instead of failing later at runtime. */
class ConfigurationPropertiesValidationTest {

    @Configuration
    @EnableConfigurationProperties({CorsProperties.class, MlServiceProperties.class})
    static class PropertiesConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfig.class);

    @Test
    void validSettingsBind() {
        runner.withPropertyValues(
                        "agrigenius.cors.allowed-origins=http://localhost:4200,https://app.example",
                        "agrigenius.ml-service.base-url=http://localhost:8001",
                        "agrigenius.ml-service.connect-timeout=2s",
                        "agrigenius.ml-service.read-timeout=5s",
                        "agrigenius.ml-service.api-key=test-only-internal-api-key-not-a-secret-0001")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(CorsProperties.class).allowedOrigins())
                            .containsExactly("http://localhost:4200", "https://app.example");
                    assertThat(context.getBean(MlServiceProperties.class).readTimeout()).isEqualTo(Duration.ofSeconds(5));
                });
    }

    @Test
    void wildcardCorsOriginIsRejected() {
        runner.withPropertyValues(
                        "agrigenius.cors.allowed-origins=*",
                        "agrigenius.ml-service.base-url=http://localhost:8001",
                        "agrigenius.ml-service.connect-timeout=2s",
                        "agrigenius.ml-service.read-timeout=5s",
                        "agrigenius.ml-service.api-key=test-only-internal-api-key-not-a-secret-0001")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("'*' is not allowed"));
    }

    @Test
    void missingMlServiceUrlIsRejected() {
        runner.withPropertyValues(
                        "agrigenius.cors.allowed-origins=http://localhost:4200",
                        "agrigenius.ml-service.connect-timeout=2s",
                        "agrigenius.ml-service.read-timeout=5s",
                        "agrigenius.ml-service.api-key=test-only-internal-api-key-not-a-secret-0001")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("baseUrl"));
    }
}
