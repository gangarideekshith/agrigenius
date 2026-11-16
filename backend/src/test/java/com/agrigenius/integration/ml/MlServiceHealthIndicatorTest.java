package com.agrigenius.integration.ml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import java.net.ConnectException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;

@ExtendWith(MockitoExtension.class)
class MlServiceHealthIndicatorTest {

    @Mock
    MlServiceClient client;

    @InjectMocks
    MlServiceHealthIndicator indicator;

    @Test
    void upWhenMlServiceAnswers() {
        given(client.health()).willReturn(new MlHealthResponse("ok", "agrigenius-ml-service", "0.1.0", "local"));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails())
                .containsEntry("service", "agrigenius-ml-service")
                .containsEntry("version", "0.1.0");
    }

    @Test
    void degradedNotDownWhenMlServiceFails() {
        given(client.health()).willThrow(new MlServiceException("down", new ConnectException("refused")));

        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(MlServiceHealthIndicator.DEGRADED);
        assertThat(health.getStatus()).isNotEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("error", "ConnectException");
    }
}
