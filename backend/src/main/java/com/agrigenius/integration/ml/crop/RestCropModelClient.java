package com.agrigenius.integration.ml.crop;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.net.ConnectException;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * {@link CropModelClient} over HTTP (contract: docs/api/contracts/crop-recommendation).
 *
 * <p>The {@link RestClient} is expected to carry the base URL, timeouts and the
 * {@code X-Internal-Api-Key} header (see {@link CropModelClientConfig}).
 *
 * <p>Error mapping: unreachable / timed out / 5xx → {@link MlServiceUnavailableException};
 * 401, other 4xx, malformed or invalid body → {@link MlServiceBadResponseException}.
 * Only a refused connection is retried, once. Feature values, response bodies and the API key
 * are never logged.
 */
public class RestCropModelClient implements CropModelClient {

    static final String PREDICT_PATH = "/v1/crop-recommendation/predict";
    static final String MODEL_PATH = "/v1/crop-recommendation/model";

    private static final Logger log = LoggerFactory.getLogger(RestCropModelClient.class);

    private final RestClient restClient;

    public RestCropModelClient(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public CropPrediction predict(CropFeatures features, UUID requestId, int topK) {
        PredictRequestWire body = new PredictRequestWire(requestId.toString(), topK, FeaturesWire.of(features));
        PredictResponseWire response = call("predict", requestId, () -> restClient.post()
                .uri(PREDICT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(PredictResponseWire.class));
        return validate(response, requestId);
    }

    @Override
    public CropModelInfo modelInfo() {
        ModelInfoWire response = call("model-info", null, () -> restClient.get()
                .uri(MODEL_PATH)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(ModelInfoWire.class));
        if (response == null || isBlank(response.name()) || isBlank(response.version()) || isBlank(response.sha256())
                || response.classes() == null || response.classes().isEmpty()) {
            throw new MlServiceBadResponseException("ML service returned incomplete model information.");
        }
        return new CropModelInfo(response.name(), response.version(), response.sha256(), response.dataLabel(),
                response.features() == null ? List.of() : List.copyOf(response.features()),
                List.copyOf(response.classes()));
    }

    private <T> T call(String operation, UUID requestId, Supplier<T> exchange) {
        for (int attempt = 1; ; attempt++) {
            try {
                return exchange.get();
            } catch (HttpServerErrorException ex) {
                log.warn("ML {} failed requestId={} status={}", operation, requestId, ex.getStatusCode().value());
                throw new MlServiceUnavailableException("The crop recommendation service is temporarily unavailable.");
            } catch (HttpClientErrorException ex) {
                if (ex.getStatusCode().value() == HttpStatus.UNAUTHORIZED.value()) {
                    log.error("ML service rejected internal API key (operation={} requestId={})", operation, requestId);
                } else {
                    log.warn("ML {} rejected requestId={} status={}", operation, requestId, ex.getStatusCode().value());
                }
                throw new MlServiceBadResponseException("The crop recommendation service rejected the request.");
            } catch (RestClientResponseException ex) {
                log.warn("ML {} unexpected status requestId={} status={}", operation, requestId, ex.getStatusCode().value());
                throw new MlServiceBadResponseException("The crop recommendation service returned an unexpected response.");
            } catch (RestClientException ex) {
                // Malformed JSON is the service's fault (502). Any other I/O problem (refused
                // connection, read timeout while waiting for the status line or body) is an outage (503).
                if (!hasCause(ex, HttpMessageNotReadableException.class)
                        && (ex instanceof ResourceAccessException || hasCause(ex, IOException.class))) {
                    if (attempt == 1 && hasCause(ex, ConnectException.class)) {
                        log.info("ML {} connection failed requestId={}, retrying once", operation, requestId);
                        continue;
                    }
                    log.warn("ML {} unreachable requestId={} cause={}", operation, requestId,
                            ex.getMostSpecificCause().getClass().getSimpleName());
                    throw new MlServiceUnavailableException("The crop recommendation service is temporarily unavailable.");
                }
                log.warn("ML {} unreadable response requestId={} cause={}", operation, requestId,
                        ex.getMostSpecificCause().getClass().getSimpleName());
                throw new MlServiceBadResponseException("The crop recommendation service returned an unreadable response.");
            }
        }
    }

    private static boolean hasCause(Throwable ex, Class<? extends Throwable> type) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (type.isInstance(cause)) {
                return true;
            }
        }
        return false;
    }

    private static CropPrediction validate(PredictResponseWire response, UUID requestId) {
        if (response == null || response.model() == null || isBlank(response.model().name())
                || isBlank(response.model().version()) || isBlank(response.model().sha256())) {
            throw invalid(requestId, "missing model identity");
        }
        if (response.predictions() == null || response.predictions().isEmpty()) {
            throw invalid(requestId, "no predictions");
        }
        List<CropPrediction.Candidate> candidates = response.predictions().stream().map(p -> {
            if (p == null || isBlank(p.crop()) || p.probability() == null || p.probability().isNaN()
                    || p.probability() < 0.0 || p.probability() > 1.0) {
                throw invalid(requestId, "invalid prediction entry");
            }
            List<CropPrediction.FeatureExplanation> explanation = p.explanation() == null ? List.of()
                    : p.explanation().stream()
                            .map(e -> new CropPrediction.FeatureExplanation(e.feature(), e.value(), e.p05(), e.p95()))
                            .toList();
            return new CropPrediction.Candidate(p.crop(), p.probability(), explanation);
        }).toList();
        List<CropPrediction.Warning> warnings = response.warnings() == null ? List.of()
                : response.warnings().stream().map(w -> new CropPrediction.Warning(w.code(), w.feature())).toList();
        ModelWire m = response.model();
        return new CropPrediction(new CropPrediction.ModelRef(m.name(), m.version(), m.sha256(), m.dataLabel()),
                candidates, warnings, response.inferenceMs() == null ? 0L : response.inferenceMs());
    }

    private static MlServiceBadResponseException invalid(UUID requestId, String reason) {
        log.warn("ML predict invalid response requestId={} reason={}", requestId, reason);
        return new MlServiceBadResponseException("The crop recommendation service returned an invalid response.");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    // ---- wire format (snake_case, see docs/api/contracts/crop-recommendation) ----

    record PredictRequestWire(
            @JsonProperty("request_id") String requestId,
            @JsonProperty("top_k") int topK,
            @JsonProperty("features") FeaturesWire features) {
    }

    record FeaturesWire(
            @JsonProperty("nitrogen") double nitrogen,
            @JsonProperty("phosphorus") double phosphorus,
            @JsonProperty("potassium") double potassium,
            @JsonProperty("temperature_c") double temperatureC,
            @JsonProperty("humidity_pct") double humidityPct,
            @JsonProperty("ph") double ph,
            @JsonProperty("rainfall_mm") double rainfallMm) {

        static FeaturesWire of(CropFeatures f) {
            return new FeaturesWire(f.nitrogen(), f.phosphorus(), f.potassium(), f.temperatureC(), f.humidityPct(),
                    f.ph(), f.rainfallMm());
        }

        @Override
        public String toString() {
            return "FeaturesWire[<redacted>]";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PredictResponseWire(
            @JsonProperty("model") ModelWire model,
            @JsonProperty("predictions") List<PredictionWire> predictions,
            @JsonProperty("warnings") List<WarningWire> warnings,
            @JsonProperty("inference_ms") Long inferenceMs) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ModelWire(
            @JsonProperty("name") String name,
            @JsonProperty("version") String version,
            @JsonProperty("sha256") String sha256,
            @JsonProperty("data_label") String dataLabel) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PredictionWire(
            @JsonProperty("crop") String crop,
            @JsonProperty("probability") Double probability,
            @JsonProperty("explanation") List<ExplanationWire> explanation) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ExplanationWire(
            @JsonProperty("feature") String feature,
            @JsonProperty("value") double value,
            @JsonProperty("p05") Double p05,
            @JsonProperty("p95") Double p95) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record WarningWire(
            @JsonProperty("code") String code,
            @JsonProperty("feature") String feature) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ModelInfoWire(
            @JsonProperty("name") String name,
            @JsonProperty("version") String version,
            @JsonProperty("sha256") String sha256,
            @JsonProperty("data_label") String dataLabel,
            @JsonProperty("features") List<String> features,
            @JsonProperty("classes") List<String> classes) {
    }
}
