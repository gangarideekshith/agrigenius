package com.agrigenius.integration.ml.crop;

import static org.assertj.core.api.Assertions.assertThat;

import com.agrigenius.integration.ml.MlServiceProperties;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

class MlServicePropertiesApiKeyTest {

    private static final String KEY = "test-only-internal-api-key-not-a-secret-0001";

    @Configuration
    @EnableConfigurationProperties(MlServiceProperties.class)
    static class PropertiesConfig {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(PropertiesConfig.class)
            .withPropertyValues(
                    "agrigenius.ml-service.base-url=http://localhost:8001",
                    "agrigenius.ml-service.connect-timeout=2s",
                    "agrigenius.ml-service.read-timeout=5s");

    @Test
    void validApiKeyBinds() {
        runner.withPropertyValues("agrigenius.ml-service.api-key=" + KEY)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(MlServiceProperties.class).apiKey()).isEqualTo(KEY);
                });
    }

    @Test
    void missingApiKeyIsRejected() {
        runner.run(context -> assertThat(context).hasFailed()
                .getFailure().rootCause().hasMessageContaining("apiKey"));
    }

    @Test
    void shortApiKeyIsRejected() {
        runner.withPropertyValues("agrigenius.ml-service.api-key=too-short-key")
                .run(context -> assertThat(context).hasFailed()
                        .getFailure().rootCause().hasMessageContaining("at least 32 characters"));
    }

    @Test
    void toStringRedactsApiKey() {
        MlServiceProperties properties = new MlServiceProperties(URI.create("http://localhost:8001"),
                Duration.ofSeconds(2), Duration.ofSeconds(5), KEY);

        assertThat(properties.toString()).doesNotContain(KEY).contains("apiKey=<redacted>");
    }
}
