package com.agrigenius.integration.ml;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.stereotype.Component;

/**
 * Reports ML service reachability as the {@code mlService} health component.
 *
 * <p>The ML service is optional for most features, so an unreachable service is reported as
 * {@link #DEGRADED}, which ranks below UP (see {@code management.endpoint.health.status.order})
 * and is excluded from the readiness group. It never takes the backend out of service.
 */
@Component
public class MlServiceHealthIndicator implements HealthIndicator {

    public static final Status DEGRADED = new Status("DEGRADED", "ML service unavailable");

    private final MlServiceClient client;

    public MlServiceHealthIndicator(MlServiceClient client) {
        this.client = client;
    }

    @Override
    public Health health() {
        try {
            MlHealthResponse response = client.health();
            return Health.up()
                    .withDetail("service", response.service())
                    .withDetail("version", response.version())
                    .build();
        } catch (MlServiceException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            return Health.status(DEGRADED)
                    .withDetail("error", cause.getClass().getSimpleName())
                    .build();
        }
    }
}
