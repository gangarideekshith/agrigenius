package com.agrigenius.integration.ml.crop;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.agrigenius.integration.ml.MlServiceProperties;
import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Contract tests against the shared fixtures in docs/api/contracts/crop-recommendation. */
class RestCropModelClientTest {

    private static final Path FIXTURES = Path.of("..", "docs", "api", "contracts", "crop-recommendation");
    private static final String BASE = "http://ml.internal";
    private static final String KEY = "test-only-internal-api-key-not-a-secret-0001";
    private static final UUID REQUEST_ID = UUID.fromString("8b0c2f1e-4a5d-4c3b-9e7f-1a2b3c4d5e6f");
    private static final CropFeatures FEATURES = new CropFeatures(90.0, 42.0, 43.0, 20.88, 82.0, 6.5, 202.94);

    private MockRestServiceServer server;
    private RestCropModelClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE)
                .defaultHeader(CropModelClientConfig.API_KEY_HEADER, KEY);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new RestCropModelClient(builder.build());
    }

    static String fixture(String name) {
        try {
            return Files.readString(FIXTURES.resolve(name), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Missing contract fixture " + name, e);
        }
    }

    @Test
    void predictSendsContractRequestWithApiKeyAndParsesResponse() {
        server.expect(requestTo(BASE + RestCropModelClient.PREDICT_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(CropModelClientConfig.API_KEY_HEADER, KEY))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json(fixture("predict-request.json"), true))
                .andRespond(withSuccess(fixture("predict-response.json"), MediaType.APPLICATION_JSON));

        CropPrediction prediction = client.predict(FEATURES, REQUEST_ID, 5);

        server.verify();
        assertThat(prediction.model()).isEqualTo(new CropPrediction.ModelRef("crop-recommendation-rf", "1.0.0",
                "3f5a1c9e0b7d2a4f6e8c1b3d5f7a9c0e2b4d6f8a1c3e5b7d9f0a2c4e6b8d0f1a", "DEMO_PUBLIC_DATASET"));
        assertThat(prediction.predictions()).hasSize(5);
        assertThat(prediction.predictions()).extracting(CropPrediction.Candidate::crop)
                .containsExactly("rice", "jute", "coconut", "papaya", "maize");
        CropPrediction.Candidate top = prediction.predictions().get(0);
        assertThat(top.probability()).isEqualTo(0.87);
        assertThat(top.explanation()).containsExactly(
                new CropPrediction.FeatureExplanation("rainfall_mm", 202.94, 182.6, 298.6),
                new CropPrediction.FeatureExplanation("humidity_pct", 82.0, 80.1, 84.9));
        assertThat(prediction.warnings()).isEmpty();
        assertThat(prediction.inferenceMs()).isEqualTo(4);
    }

    @Test
    void parsesLowConfidenceResponseWithWarnings() {
        server.expect(requestTo(BASE + RestCropModelClient.PREDICT_PATH))
                .andRespond(withSuccess(fixture("predict-response-low-confidence.json"), MediaType.APPLICATION_JSON));

        CropPrediction prediction = client.predict(FEATURES, REQUEST_ID, 5);

        assertThat(prediction.predictions().get(0).crop()).isEqualTo("cotton");
        assertThat(prediction.predictions().get(0).probability()).isEqualTo(0.24);
        assertThat(prediction.predictions().get(1).explanation()).isEmpty();
        assertThat(prediction.warnings())
                .containsExactly(new CropPrediction.Warning("INPUT_OUTSIDE_TRAINING_RANGE", "rainfall_mm"));
    }

    @Test
    void modelInfoParsesFixtureIgnoringExtraFields() {
        server.expect(requestTo(BASE + RestCropModelClient.MODEL_PATH))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(CropModelClientConfig.API_KEY_HEADER, KEY))
                .andRespond(withSuccess(fixture("model-info-response.json"), MediaType.APPLICATION_JSON));

        CropModelInfo info = client.modelInfo();

        assertThat(info.name()).isEqualTo("crop-recommendation-rf");
        assertThat(info.version()).isEqualTo("1.0.0");
        assertThat(info.dataLabel()).isEqualTo("DEMO_PUBLIC_DATASET");
        assertThat(info.features()).containsExactly("nitrogen", "phosphorus", "potassium", "temperature_c",
                "humidity_pct", "ph", "rainfall_mm");
        assertThat(info.classes()).hasSize(22).contains("rice", "maize", "coffee");
    }

    @Test
    void serviceUnavailableBecomesUnavailable() {
        server.expect(requestTo(BASE + RestCropModelClient.PREDICT_PATH))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(fixture("error-model-not-loaded.json")));

        assertThatThrownBy(() -> client.predict(FEATURES, REQUEST_ID, 5))
                .isInstanceOf(MlServiceUnavailableException.class)
                .satisfies(ex -> assertThat(((MlServiceUnavailableException) ex).code()).isEqualTo("ML_SERVICE_UNAVAILABLE"))
                .satisfies(ex -> assertThat(((MlServiceUnavailableException) ex).status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void internalServerErrorBecomesUnavailableWithoutRetry() {
        server.expect(times(1), requestTo(BASE + RestCropModelClient.PREDICT_PATH)).andRespond(withServerError());

        assertThatThrownBy(() -> client.predict(FEATURES, REQUEST_ID, 5))
                .isInstanceOf(MlServiceUnavailableException.class);
        server.verify();
    }

    @Test
    void unauthorizedBecomesBadResponse() {
        server.expect(requestTo(BASE + RestCropModelClient.PREDICT_PATH))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(fixture("error-unauthorized.json")));

        assertThatThrownBy(() -> client.predict(FEATURES, REQUEST_ID, 5))
                .isInstanceOf(MlServiceBadResponseException.class)
                .satisfies(ex -> assertThat(((MlServiceBadResponseException) ex).code()).isEqualTo("ML_SERVICE_ERROR"))
                .satisfies(ex -> assertThat(((MlServiceBadResponseException) ex).status()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    @Test
    void validationErrorBecomesBadResponse() {
        server.expect(requestTo(BASE + RestCropModelClient.PREDICT_PATH))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(fixture("error-validation.json")));

        assertThatThrownBy(() -> client.predict(FEATURES, REQUEST_ID, 5))
                .isInstanceOf(MlServiceBadResponseException.class);
    }

    @Test
    void malformedJsonBecomesBadResponse() {
        server.expect(requestTo(BASE + RestCropModelClient.PREDICT_PATH))
                .andRespond(withSuccess("{not json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.predict(FEATURES, REQUEST_ID, 5))
                .isInstanceOf(MlServiceBadResponseException.class);
    }

    @Test
    void probabilityAboveOneIsRejected() {
        String body = fixture("predict-response.json").replace("\"probability\": 0.87", "\"probability\": 1.5");
        server.expect(requestTo(BASE + RestCropModelClient.PREDICT_PATH))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.predict(FEATURES, REQUEST_ID, 5))
                .isInstanceOf(MlServiceBadResponseException.class);
    }

    @Test
    void emptyPredictionsAreRejected() {
        String body = """
                { "model": { "name": "m", "version": "1", "sha256": "abc", "data_label": "X" },
                  "predictions": [], "warnings": [], "inference_ms": 1 }
                """;
        server.expect(requestTo(BASE + RestCropModelClient.PREDICT_PATH))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.predict(FEATURES, REQUEST_ID, 5))
                .isInstanceOf(MlServiceBadResponseException.class);
    }

    @Test
    void missingModelIdentityIsRejected() {
        String body = """
                { "predictions": [ { "crop": "rice", "probability": 0.9, "explanation": [] } ] }
                """;
        server.expect(requestTo(BASE + RestCropModelClient.PREDICT_PATH))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.predict(FEATURES, REQUEST_ID, 5))
                .isInstanceOf(MlServiceBadResponseException.class);
    }

    @Test
    void blankCropIsRejected() {
        String body = fixture("predict-response.json").replace("\"crop\": \"rice\"", "\"crop\": \" \"");
        server.expect(requestTo(BASE + RestCropModelClient.PREDICT_PATH))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.predict(FEATURES, REQUEST_ID, 5))
                .isInstanceOf(MlServiceBadResponseException.class);
    }

    @Test
    void incompleteModelInfoIsRejected() {
        server.expect(requestTo(BASE + RestCropModelClient.MODEL_PATH))
                .andRespond(withSuccess("{\"name\":\"m\",\"version\":\"1\",\"sha256\":\"abc\",\"classes\":[]}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.modelInfo()).isInstanceOf(MlServiceBadResponseException.class);
    }

    @Test
    void connectionFailureIsRetriedExactlyOnceThenUnavailable() {
        server.expect(times(2), requestTo(BASE + RestCropModelClient.PREDICT_PATH))
                .andRespond(withException(new ConnectException("Connection refused")));

        assertThatThrownBy(() -> client.predict(FEATURES, REQUEST_ID, 5))
                .isInstanceOf(MlServiceUnavailableException.class);
        server.verify();
    }

    @Test
    void connectionFailureThenSuccessRecovers() {
        server.expect(requestTo(BASE + RestCropModelClient.PREDICT_PATH))
                .andRespond(withException(new ConnectException("Connection refused")));
        server.expect(requestTo(BASE + RestCropModelClient.PREDICT_PATH))
                .andRespond(withSuccess(fixture("predict-response.json"), MediaType.APPLICATION_JSON));

        assertThat(client.predict(FEATURES, REQUEST_ID, 5).predictions()).hasSize(5);
        server.verify();
    }

    @Test
    void readTimeoutIsNotRetried() {
        server.expect(times(1), requestTo(BASE + RestCropModelClient.PREDICT_PATH))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatThrownBy(() -> client.predict(FEATURES, REQUEST_ID, 5))
                .isInstanceOf(MlServiceUnavailableException.class);
        server.verify();
    }

    @Test
    void realClosedPortIsUnavailable() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        CropModelClient real = realClient(URI.create("http://127.0.0.1:" + closedPort), Duration.ofMillis(500),
                Duration.ofMillis(500));

        assertThatThrownBy(() -> real.predict(FEATURES, REQUEST_ID, 5))
                .isInstanceOf(MlServiceUnavailableException.class);
    }

    @Test
    void realHangingServiceTimesOutOnceWithinReadTimeout() throws Exception {
        AtomicInteger connections = new AtomicInteger();
        try (ServerSocket hanging = new ServerSocket(0, 10, InetAddress.getLoopbackAddress())) {
            hanging.setSoTimeout(3000);
            Thread acceptor = Thread.ofVirtual().start(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try (Socket ignored = hanging.accept()) {
                        connections.incrementAndGet();
                        Thread.sleep(3000);
                    } catch (Exception stop) {
                        return;
                    }
                }
            });
            CropModelClient real = realClient(URI.create("http://127.0.0.1:" + hanging.getLocalPort()),
                    Duration.ofMillis(500), Duration.ofMillis(300));

            long start = System.nanoTime();
            assertThatThrownBy(() -> real.predict(FEATURES, REQUEST_ID, 5))
                    .isInstanceOf(MlServiceUnavailableException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
            assertThat(connections.get()).as("read timeouts are not retried").isEqualTo(1);
            acceptor.interrupt();
        }
    }

    private static CropModelClient realClient(URI baseUrl, Duration connectTimeout, Duration readTimeout) {
        MlServiceProperties properties = new MlServiceProperties(baseUrl, connectTimeout, readTimeout, KEY);
        CropModelClientConfig config = new CropModelClientConfig();
        return config.cropModelClient(config.cropModelRestClient(RestClient.builder(), properties));
    }
}
