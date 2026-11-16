package com.agrigenius.integration.ml;

/** Response of the ML service's {@code GET /health} (see docs/api/API_CONTRACT.md). */
public record MlHealthResponse(String status, String service, String version, String environment) {
}
