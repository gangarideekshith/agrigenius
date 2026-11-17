package com.agrigenius.recommendation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.agrigenius.common.error.TooManyRequestsException;
import com.agrigenius.integration.ml.crop.CropFeatures;
import com.agrigenius.integration.ml.crop.CropModelClient;
import com.agrigenius.integration.ml.crop.CropPrediction;
import com.agrigenius.integration.ml.crop.CropPrediction.Candidate;
import com.agrigenius.integration.ml.crop.CropPrediction.FeatureExplanation;
import com.agrigenius.integration.ml.crop.CropPrediction.ModelRef;
import com.agrigenius.integration.ml.crop.CropPrediction.Warning;
import com.agrigenius.integration.ml.crop.MlServiceBadResponseException;
import com.agrigenius.integration.ml.crop.MlServiceUnavailableException;
import com.agrigenius.recommendation.api.dto.CreateRecommendationRequest;
import com.agrigenius.recommendation.api.dto.RecommendationResponse;
import com.agrigenius.recommendation.config.RecommendationProperties;
import com.agrigenius.recommendation.domain.ConfidenceLevel;
import com.agrigenius.recommendation.domain.Crop;
import com.agrigenius.recommendation.domain.CropRecommendationRequest;
import com.agrigenius.recommendation.domain.RecommendationStatus;
import com.agrigenius.recommendation.infrastructure.CropRecommendationRequestRepository;
import com.agrigenius.recommendation.infrastructure.CropRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

class RecommendationServiceTest {

    private static final UUID USER = UUID.randomUUID();
    private static final Set<String> KNOWN_CROPS = Set.of("rice", "jute", "coconut", "papaya", "maize", "cotton");
    private static final ModelRef MODEL = new ModelRef("crop-recommendation-rf", "1.0.0", "a".repeat(64),
            "DEMO_PUBLIC_DATASET");

    private CropRecommendationRequestRepository requests;
    private CropRepository crops;
    private CropModelClient model;
    private RecommendationService service;

    /** Simulated table, plus the status each save() saw (entities are mutable). */
    private final Map<UUID, CropRecommendationRequest> stored = new HashMap<>();
    private final List<RecommendationStatus> savedStatuses = new ArrayList<>();

    @BeforeEach
    void setUp() {
        requests = mock(CropRecommendationRequestRepository.class);
        crops = mock(CropRepository.class);
        model = mock(CropModelClient.class);
        given(requests.save(any())).willAnswer(invocation -> {
            CropRecommendationRequest request = invocation.getArgument(0);
            savedStatuses.add(request.getStatus());
            stored.put(request.getId(), request);
            return request;
        });
        given(requests.findById(any())).willAnswer(invocation -> Optional.ofNullable(stored.get(invocation.getArgument(0))));
        given(crops.findAllById(anyIterable())).willAnswer(invocation -> {
            Iterable<String> codes = invocation.getArgument(0);
            return StreamSupport.stream(codes.spliterator(), false)
                    .filter(KNOWN_CROPS::contains)
                    .map(code -> new Crop(code, code.substring(0, 1).toUpperCase() + code.substring(1)))
                    .toList();
        });
        service = newService(30);
    }

    private RecommendationService newService(int perUserPerHour) {
        RecommendationProperties properties = new RecommendationProperties(
                new RecommendationProperties.Confidence(0.60, 0.30),
                new RecommendationProperties.Throttle(perUserPerHour));
        return new RecommendationService(requests, crops, model, properties,
                Clock.fixed(Instant.parse("2026-11-17T10:15:00Z"), ZoneOffset.UTC),
                new TransactionTemplate(mock(PlatformTransactionManager.class)), JsonMapper.builder().build());
    }

    static CreateRecommendationRequest request() {
        return new CreateRecommendationRequest(
                new CreateRecommendationRequest.Soil(90.0, 42.0, 43.0, 6.5, "loamy", 30.0),
                new CreateRecommendationRequest.Climate(20.876, 82.0, 202.935),
                "kharif", true, "maize",
                new CreateRecommendationRequest.Location("Telangana", "Warangal"));
    }

    private static CropPrediction prediction(double topProbability, List<Warning> warnings) {
        return new CropPrediction(MODEL, List.of(
                new Candidate("rice", topProbability, List.of(
                        new FeatureExplanation("rainfall_mm", 202.94, 182.6, 298.6),
                        new FeatureExplanation("humidity_pct", 82.0, 85.0, 90.0))),
                new Candidate("jute", 0.06, List.of()),
                new Candidate("coconut", 0.03, List.of()),
                new Candidate("papaya", 0.02, List.of()),
                new Candidate("maize", 0.01, List.of())), warnings, 4);
    }

    private CropRecommendationRequest onlyStored() {
        assertThat(stored).hasSize(1);
        return stored.values().iterator().next();
    }

    @Test
    void storesPendingBeforeCallingTheModelThenCompletes() {
        given(model.predict(any(), any(), anyInt())).willAnswer(invocation -> {
            assertThat(savedStatuses).containsExactly(RecommendationStatus.PENDING);
            return prediction(0.87, List.of());
        });

        RecommendationResponse response = service.create(USER, request());

        assertThat(savedStatuses).containsExactly(RecommendationStatus.PENDING, RecommendationStatus.COMPLETED);
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.advisory()).isEqualTo(RecommendationMapper.ADVISORY);
        CropRecommendationRequest entity = onlyStored();
        assertThat(entity.getUserId()).isEqualTo(USER);
        assertThat(entity.getModelName()).isEqualTo("crop-recommendation-rf");
        assertThat(entity.getModelVersion()).isEqualTo("1.0.0");
        assertThat(entity.getModelSha256()).isEqualTo("a".repeat(64));
        assertThat(entity.getModelDataLabel()).isEqualTo("DEMO_PUBLIC_DATASET");
        assertThat(entity.getCompletedAt()).isNotNull();
    }

    @Test
    void mapsCandidatesScoresConfidenceExplanationAndEconomics() {
        given(model.predict(any(), any(), anyInt())).willReturn(prediction(0.87, List.of()));

        RecommendationResponse response = service.create(USER, request());

        assertThat(response.candidates()).hasSize(5);
        RecommendationResponse.Candidate top = response.candidates().get(0);
        assertThat(top.rank()).isEqualTo(1);
        assertThat(top.crop().code()).isEqualTo("rice");
        assertThat(top.crop().name()).isEqualTo("Rice");
        assertThat(top.suitabilityScore()).isEqualTo(87);
        assertThat(top.probability()).isEqualTo(0.87);
        assertThat(top.confidence()).isEqualTo("HIGH");
        assertThat(top.economics().available()).isFalse();
        assertThat(top.economics().reason()).isNotBlank();
        assertThat(top.explanation()).extracting(RecommendationResponse.Explanation::feature)
                .containsExactly("rainfallMm", "humidityPct");
        assertThat(top.explanation().get(0).fit()).isEqualTo("WITHIN");
        assertThat(top.explanation().get(0).typicalRange()).containsExactly(182.6, 298.6);
        assertThat(top.explanation().get(1).fit()).isEqualTo("BELOW");
        assertThat(response.candidates()).extracting(RecommendationResponse.Candidate::rank)
                .containsExactly(1, 2, 3, 4, 5);
        assertThat(response.candidates().get(4).confidence()).isEqualTo("LOW");
        assertThat(response.featuresUsedByModel()).containsExactly(
                "nitrogenKgHa", "phosphorusKgHa", "potassiumKgHa", "ph", "temperatureC", "humidityPct", "rainfallMm");
        assertThat(response.model().dataLabel()).isEqualTo("DEMO_PUBLIC_DATASET");
    }

    @Test
    void confidenceThresholds() {
        assertThat(service.confidenceOf(0.60)).isEqualTo(ConfidenceLevel.HIGH);
        assertThat(service.confidenceOf(0.5999)).isEqualTo(ConfidenceLevel.MEDIUM);
        assertThat(service.confidenceOf(0.30)).isEqualTo(ConfidenceLevel.MEDIUM);
        assertThat(service.confidenceOf(0.2999)).isEqualTo(ConfidenceLevel.LOW);
        assertThat(service.confidenceOf(1.0)).isEqualTo(ConfidenceLevel.HIGH);
        assertThat(service.confidenceOf(0.0)).isEqualTo(ConfidenceLevel.LOW);
    }

    @Test
    void lowTopCandidateMeansLowConfidence() {
        given(model.predict(any(), any(), anyInt())).willReturn(prediction(0.24, List.of()));

        RecommendationResponse response = service.create(USER, request());

        assertThat(response.status()).isEqualTo("LOW_CONFIDENCE");
        assertThat(response.advisory()).isEqualTo(RecommendationMapper.LOW_CONFIDENCE_ADVISORY);
        assertThat(onlyStored().getStatus()).isEqualTo(RecommendationStatus.LOW_CONFIDENCE);
    }

    @Test
    void anyWarningMeansLowConfidenceAndWarningFeatureIsMapped() {
        given(model.predict(any(), any(), anyInt()))
                .willReturn(prediction(0.87, List.of(new Warning("INPUT_OUTSIDE_TRAINING_RANGE", "rainfall_mm"))));

        RecommendationResponse response = service.create(USER, request());

        assertThat(response.status()).isEqualTo("LOW_CONFIDENCE");
        assertThat(response.warnings()).containsExactly(
                new RecommendationResponse.Warning("INPUT_OUTSIDE_TRAINING_RANGE", "rainfallMm"));
        assertThat(onlyStored().getWarningsJson()).contains("rainfallMm");
    }

    @Test
    void featuresSentToModelAreTheRoundedInputs() {
        given(model.predict(any(), any(), anyInt())).willReturn(prediction(0.87, List.of()));

        service.create(USER, request());

        ArgumentCaptor<CropFeatures> features = ArgumentCaptor.forClass(CropFeatures.class);
        ArgumentCaptor<UUID> requestId = ArgumentCaptor.forClass(UUID.class);
        verify(model).predict(features.capture(), requestId.capture(), eq(5));
        assertThat(features.getValue()).isEqualTo(new CropFeatures(90.0, 42.0, 43.0, 20.88, 82.0, 6.5, 202.94));
        assertThat(requestId.getValue()).isEqualTo(onlyStored().getId());
        assertThat(onlyStored().inputs().soilType()).isEqualTo("LOAMY");
        assertThat(onlyStored().inputs().season()).isEqualTo("KHARIF");
        assertThat(onlyStored().inputs().previousCropCode()).isEqualTo("maize");
    }

    @Test
    void mlUnavailableMarksFailedAndRethrows() {
        given(model.predict(any(), any(), anyInt())).willThrow(new MlServiceUnavailableException("down"));

        assertThatThrownBy(() -> service.create(USER, request()))
                .isInstanceOf(MlServiceUnavailableException.class);

        CropRecommendationRequest entity = onlyStored();
        assertThat(entity.getStatus()).isEqualTo(RecommendationStatus.FAILED);
        assertThat(entity.getFailureCode()).isEqualTo("ML_SERVICE_UNAVAILABLE");
        assertThat(entity.getResults()).isEmpty();
    }

    @Test
    void mlBadResponseMarksFailedAndRethrows() {
        given(model.predict(any(), any(), anyInt())).willThrow(new MlServiceBadResponseException("bad"));

        assertThatThrownBy(() -> service.create(USER, request()))
                .isInstanceOf(MlServiceBadResponseException.class);

        assertThat(onlyStored().getFailureCode()).isEqualTo("ML_SERVICE_ERROR");
    }

    @Test
    void unknownCropCodeIsBadGatewayAndFailed() {
        given(model.predict(any(), any(), anyInt())).willReturn(new CropPrediction(MODEL,
                List.of(new Candidate("dragonfruit", 0.9, List.of())), List.of(), 3));

        assertThatThrownBy(() -> service.create(USER, request()))
                .isInstanceOf(MlServiceBadResponseException.class)
                .satisfies(ex -> assertThat(((MlServiceBadResponseException) ex).code()).isEqualTo("ML_SERVICE_ERROR"));

        assertThat(onlyStored().getStatus()).isEqualTo(RecommendationStatus.FAILED);
        assertThat(onlyStored().getFailureCode()).isEqualTo("ML_SERVICE_ERROR");
    }

    @Test
    void emptyOrInvalidProbabilitiesAreBadGateway() {
        given(model.predict(any(), any(), anyInt()))
                .willReturn(new CropPrediction(MODEL, List.of(), List.of(), 1))
                .willReturn(new CropPrediction(MODEL, List.of(new Candidate("rice", 1.4, List.of())), List.of(), 1));

        assertThatThrownBy(() -> service.create(USER, request())).isInstanceOf(MlServiceBadResponseException.class);
        assertThatThrownBy(() -> service.create(USER, request())).isInstanceOf(MlServiceBadResponseException.class);
        assertThat(stored.values()).allMatch(r -> r.getStatus() == RecommendationStatus.FAILED);
    }

    @Test
    void unexpectedClientErrorStillMarksFailed() {
        given(model.predict(any(), any(), anyInt())).willThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> service.create(USER, request())).isInstanceOf(IllegalStateException.class);

        assertThat(onlyStored().getFailureCode()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void perUserThrottleBlocksBeforeCallingTheModel() {
        RecommendationService throttled = newService(2);
        given(model.predict(any(), any(), anyInt())).willReturn(prediction(0.87, List.of()));

        throttled.create(USER, request());
        throttled.create(USER, request());
        assertThatThrownBy(() -> throttled.create(USER, request()))
                .isInstanceOf(TooManyRequestsException.class);

        verify(model, times(2)).predict(any(), any(), anyInt());
        throttled.create(UUID.randomUUID(), request()); // other users are not affected
    }

    @Test
    void getOtherUsersRecommendationIsNotFound() {
        UUID id = UUID.randomUUID();
        given(requests.findByIdAndUserId(id, USER)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(USER, id))
                .isInstanceOf(RecommendationNotFoundException.class)
                .satisfies(ex -> assertThat(((RecommendationNotFoundException) ex).code()).isEqualTo("NOT_FOUND"));
        verify(model, never()).predict(any(), any(), anyInt());
    }

    @Test
    void getOwnRecommendationReturnsStoredResult() {
        given(model.predict(any(), any(), anyInt())).willReturn(prediction(0.87, List.of()));
        RecommendationResponse created = service.create(USER, request());
        given(requests.findByIdAndUserId(created.id(), USER)).willReturn(Optional.of(onlyStored()));

        RecommendationResponse fetched = service.get(USER, created.id());

        assertThat(fetched).usingRecursiveComparison().isEqualTo(created);
    }
}
