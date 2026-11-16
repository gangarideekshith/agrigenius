package com.agrigenius.integration.ml;

/**
 * Port to the private Python ML service. Feature modules depend on this interface, never on
 * HTTP details, so the transport can change without touching business code.
 */
public interface MlServiceClient {

    /**
     * Calls the ML service liveness endpoint.
     *
     * @throws MlServiceException if the service is unreachable, times out or answers badly
     */
    MlHealthResponse health();
}
