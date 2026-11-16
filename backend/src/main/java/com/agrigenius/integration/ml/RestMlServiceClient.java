package com.agrigenius.integration.ml;

import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** {@link MlServiceClient} over HTTP using a {@link RestClient} configured with the service base URL and timeouts. */
public class RestMlServiceClient implements MlServiceClient {

    private final RestClient restClient;

    public RestMlServiceClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public MlHealthResponse health() {
        MlHealthResponse response;
        try {
            response = restClient.get()
                    .uri("/health")
                    .retrieve()
                    .body(MlHealthResponse.class);
        } catch (RestClientException ex) {
            throw new MlServiceException("ML service health check failed", ex);
        }
        if (response == null || !"ok".equals(response.status())) {
            throw new MlServiceException("ML service reported an unexpected health status");
        }
        return response;
    }
}
