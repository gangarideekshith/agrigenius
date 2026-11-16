package com.agrigenius.integration.ml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RestMlServiceClientTest {

    private static final String HEALTH_JSON =
            "{\"status\":\"ok\",\"service\":\"agrigenius-ml-service\",\"version\":\"0.1.0\",\"environment\":\"local\"}";

    @Test
    void parsesHealthResponse() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ml.internal");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://ml.internal/health"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(HEALTH_JSON, MediaType.APPLICATION_JSON));

        MlHealthResponse response = new RestMlServiceClient(builder.build()).health();

        assertThat(response.status()).isEqualTo("ok");
        assertThat(response.service()).isEqualTo("agrigenius-ml-service");
        assertThat(response.version()).isEqualTo("0.1.0");
        server.verify();
    }

    @Test
    void serverErrorBecomesMlServiceException() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ml.internal");
        MockRestServiceServer.bindTo(builder).build()
                .expect(requestTo("http://ml.internal/health"))
                .andRespond(withServerError());

        assertThatThrownBy(() -> new RestMlServiceClient(builder.build()).health())
                .isInstanceOf(MlServiceException.class);
    }

    @Test
    void unexpectedStatusValueIsRejected() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ml.internal");
        MockRestServiceServer.bindTo(builder).build()
                .expect(requestTo("http://ml.internal/health"))
                .andRespond(withSuccess("{\"status\":\"starting\"}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> new RestMlServiceClient(builder.build()).health())
                .isInstanceOf(MlServiceException.class)
                .hasMessageContaining("unexpected health status");
    }

    @Test
    void hangingServiceTimesOutWithinReadTimeout() throws Exception {
        // A plain blocking socket that accepts the connection but never answers, like a hung service.
        try (ServerSocket hanging = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            Thread acceptor = Thread.ofVirtual().start(() -> {
                try (Socket ignored = hanging.accept()) {
                    Thread.sleep(5000);
                } catch (Exception ignored) {
                    // test finished or socket closed
                }
            });
            MlServiceClient client = clientFor(URI.create("http://127.0.0.1:" + hanging.getLocalPort()),
                    Duration.ofMillis(500), Duration.ofMillis(300));

            long start = System.nanoTime();
            assertThatThrownBy(client::health).isInstanceOf(MlServiceException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
            acceptor.interrupt();
        }
    }

    @Test
    void unreachableServiceFailsFast() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        MlServiceClient client = clientFor(URI.create("http://127.0.0.1:" + closedPort),
                Duration.ofMillis(500), Duration.ofMillis(500));

        assertThatThrownBy(client::health).isInstanceOf(MlServiceException.class);
    }

    private static MlServiceClient clientFor(URI baseUrl, Duration connectTimeout, Duration readTimeout) {
        MlServiceProperties properties = new MlServiceProperties(baseUrl, connectTimeout, readTimeout);
        RestClient restClient = new MlServiceClientConfig().mlServiceRestClient(RestClient.builder(), properties);
        return new RestMlServiceClient(restClient);
    }
}
