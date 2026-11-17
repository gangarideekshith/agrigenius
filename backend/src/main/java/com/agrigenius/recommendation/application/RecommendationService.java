package com.agrigenius.recommendation.application;

import com.agrigenius.common.error.ApiException;
import com.agrigenius.common.error.TooManyRequestsException;
import com.agrigenius.identity.application.FixedWindowCounter;
import com.agrigenius.integration.ml.crop.CropFeatures;
import com.agrigenius.integration.ml.crop.CropModelClient;
import com.agrigenius.integration.ml.crop.CropPrediction;
import com.agrigenius.integration.ml.crop.MlServiceBadResponseException;
import com.agrigenius.recommendation.api.dto.CreateRecommendationRequest;
import com.agrigenius.recommendation.api.dto.PageResponse;
import com.agrigenius.recommendation.api.dto.RecommendationResponse;
import com.agrigenius.recommendation.api.dto.RecommendationSummary;
import com.agrigenius.recommendation.application.RecommendationMapper.StoredExplanation;
import com.agrigenius.recommendation.application.RecommendationMapper.StoredWarning;
import com.agrigenius.recommendation.config.RecommendationProperties;
import com.agrigenius.recommendation.domain.ConfidenceLevel;
import com.agrigenius.recommendation.domain.Crop;
import com.agrigenius.recommendation.domain.CropRecommendationRequest;
import com.agrigenius.recommendation.domain.RecommendationInputs;
import com.agrigenius.recommendation.domain.RecommendationStatus;
import com.agrigenius.recommendation.infrastructure.CropRecommendationRequestRepository;
import com.agrigenius.recommendation.infrastructure.CropRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Crop recommendations for the signed-in farmer.
 *
 * <p>The request is stored as PENDING before the ML call, the ML call runs outside any database
 * transaction (it can take seconds), and the outcome is stored afterwards: results and model
 * identity on success, FAILED with a failure code otherwise.
 */
@Service
public class RecommendationService {

    static final int TOP_K = 5;
    static final Duration THROTTLE_WINDOW = Duration.ofHours(1);
    static final int THROTTLE_MAX_KEYS = 100_000;

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private final CropRecommendationRequestRepository requests;
    private final CropRepository crops;
    private final CropModelClient model;
    private final RecommendationProperties properties;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final RecommendationMapper mapper;
    private final FixedWindowCounter perUserThrottle;

    public RecommendationService(CropRecommendationRequestRepository requests, CropRepository crops,
            CropModelClient model, RecommendationProperties properties, Clock clock, TransactionTemplate tx,
            JsonMapper jsonMapper) {
        this.requests = requests;
        this.crops = crops;
        this.model = model;
        this.properties = properties;
        this.clock = clock;
        this.tx = tx;
        this.mapper = new RecommendationMapper(jsonMapper);
        this.perUserThrottle = new FixedWindowCounter(properties.throttle().perUserPerHour(), THROTTLE_WINDOW, clock,
                THROTTLE_MAX_KEYS);
    }

    public RecommendationResponse create(UUID userId, CreateRecommendationRequest dto) {
        String throttleKey = userId.toString();
        if (!perUserThrottle.tryIncrement(throttleKey)) {
            throw new TooManyRequestsException(perUserThrottle.retryAfter(throttleKey));
        }

        RecommendationInputs inputs = toInputs(dto);
        CropRecommendationRequest pending = new CropRecommendationRequest(userId, inputs, now());
        UUID requestId = pending.getId();
        tx.executeWithoutResult(status -> requests.save(pending));

        CropPrediction prediction;
        try {
            prediction = model.predict(toFeatures(inputs), requestId, TOP_K);
        } catch (ApiException mlFailure) {
            markFailed(requestId, mlFailure.code());
            log.warn("Crop recommendation failed requestId={} code={}", requestId, mlFailure.code());
            throw mlFailure;
        } catch (RuntimeException unexpected) {
            markFailed(requestId, "INTERNAL_ERROR");
            throw unexpected;
        }

        Map<String, String> cropNames = validatePrediction(prediction, requestId);
        RecommendationResponse response = tx.execute(status -> {
            CropRecommendationRequest request = requests.findById(requestId).orElseThrow();
            store(request, prediction);
            return mapper.toResponse(requests.save(request), cropNames);
        });
        log.info("Crop recommendation stored requestId={} status={}", requestId, response.status());
        return response;
    }

    @Transactional(readOnly = true)
    public RecommendationResponse get(UUID userId, UUID id) {
        CropRecommendationRequest request = requests.findByIdAndUserId(id, userId)
                .orElseThrow(RecommendationNotFoundException::new);
        return mapper.toResponse(request, cropNames(request.getResults().stream()
                .map(r -> r.getCropCode()).collect(Collectors.toSet())));
    }

    @Transactional(readOnly = true)
    public PageResponse<RecommendationSummary> history(UUID userId, int page, int size) {
        Page<CropRecommendationRequest> result =
                requests.findByUserIdOrderByRequestedAtDesc(userId, PageRequest.of(page, size));
        Set<String> codes = new HashSet<>();
        result.forEach(r -> r.getResults().stream().findFirst().ifPresent(top -> codes.add(top.getCropCode())));
        Map<String, String> names = cropNames(codes);
        List<RecommendationSummary> items = result.getContent().stream()
                .map(r -> mapper.toSummary(r, names))
                .toList();
        return new PageResponse<>(items, page, size, result.getTotalElements(), result.getTotalPages());
    }

    /** Confidence level for one probability, using the configured thresholds (decision Q8). */
    ConfidenceLevel confidenceOf(double probability) {
        RecommendationProperties.Confidence thresholds = properties.confidence();
        if (probability >= thresholds.high()) {
            return ConfidenceLevel.HIGH;
        }
        return probability >= thresholds.medium() ? ConfidenceLevel.MEDIUM : ConfidenceLevel.LOW;
    }

    private Map<String, String> validatePrediction(CropPrediction prediction, UUID requestId) {
        if (prediction == null || prediction.model() == null || prediction.predictions() == null
                || prediction.predictions().isEmpty() || prediction.predictions().size() > TOP_K) {
            markFailed(requestId, "ML_SERVICE_ERROR");
            throw new MlServiceBadResponseException("The recommendation service returned an unusable response.");
        }
        Set<String> codes = prediction.predictions().stream().map(CropPrediction.Candidate::crop)
                .collect(Collectors.toSet());
        Map<String, String> names = cropNames(codes);
        boolean invalid = prediction.predictions().stream().anyMatch(c ->
                c.crop() == null || !names.containsKey(c.crop())
                        || Double.isNaN(c.probability()) || c.probability() < 0 || c.probability() > 1);
        if (invalid) {
            markFailed(requestId, "ML_SERVICE_ERROR");
            throw new MlServiceBadResponseException("The recommendation service returned an unknown crop.");
        }
        return names;
    }

    private void store(CropRecommendationRequest request, CropPrediction prediction) {
        List<CropPrediction.Candidate> candidates = prediction.predictions();
        short rank = 1;
        for (CropPrediction.Candidate candidate : candidates) {
            List<StoredExplanation> explanation = candidate.explanation() == null ? List.of()
                    : candidate.explanation().stream()
                            .map(e -> new StoredExplanation(RecommendationMapper.apiFeatureName(e.feature()),
                                    e.value(), e.p05(), e.p95()))
                            .toList();
            BigDecimal probability = BigDecimal.valueOf(candidate.probability()).setScale(4, RoundingMode.HALF_UP);
            request.addResult(rank++, candidate.crop(), probability,
                    (short) Math.round(candidate.probability() * 100), confidenceOf(candidate.probability()),
                    mapper.toJson(explanation));
        }
        List<StoredWarning> warnings = prediction.warnings() == null ? List.of()
                : prediction.warnings().stream()
                        .map(w -> new StoredWarning(w.code(), RecommendationMapper.apiFeatureName(w.feature())))
                        .toList();
        boolean lowConfidence = confidenceOf(candidates.get(0).probability()) == ConfidenceLevel.LOW
                || !warnings.isEmpty();
        CropPrediction.ModelRef ref = prediction.model();
        request.complete(lowConfidence ? RecommendationStatus.LOW_CONFIDENCE : RecommendationStatus.COMPLETED,
                ref.name(), ref.version(), ref.sha256(), ref.dataLabel(), mapper.toJson(warnings), now());
    }

    private void markFailed(UUID requestId, String failureCode) {
        tx.executeWithoutResult(status -> requests.findById(requestId)
                .ifPresent(request -> {
                    request.markFailed(failureCode, now());
                    requests.save(request);
                }));
    }

    private Map<String, String> cropNames(Collection<String> codes) {
        if (codes.isEmpty()) {
            return Map.of();
        }
        return crops.findAllById(codes).stream().collect(Collectors.toMap(Crop::getCode, Crop::getNameEn,
                (a, b) -> a));
    }

    static RecommendationInputs toInputs(CreateRecommendationRequest dto) {
        CreateRecommendationRequest.Soil soil = dto.soil();
        CreateRecommendationRequest.Climate climate = dto.climate();
        CreateRecommendationRequest.Location location = dto.location();
        return new RecommendationInputs(
                scale(soil.nitrogenKgHa()), scale(soil.phosphorusKgHa()), scale(soil.potassiumKgHa()),
                scale(soil.ph()), scale(climate.temperatureC()), scale(climate.humidityPct()),
                scale(climate.rainfallMm()), soil.soilType(), scale(soil.moisturePct()), dto.season(),
                dto.irrigationAvailable(), dto.previousCrop(),
                location == null ? null : location.state(), location == null ? null : location.district());
    }

    static CropFeatures toFeatures(RecommendationInputs inputs) {
        Function<BigDecimal, Double> d = BigDecimal::doubleValue;
        return new CropFeatures(d.apply(inputs.nitrogenKgHa()), d.apply(inputs.phosphorusKgHa()),
                d.apply(inputs.potassiumKgHa()), d.apply(inputs.temperatureC()), d.apply(inputs.humidityPct()),
                d.apply(inputs.ph()), d.apply(inputs.rainfallMm()));
    }

    private static BigDecimal scale(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
