package com.agrigenius.recommendation.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrigenius.common.error.TooManyRequestsException;
import com.agrigenius.integration.ml.crop.MlServiceBadResponseException;
import com.agrigenius.integration.ml.crop.MlServiceUnavailableException;
import com.agrigenius.recommendation.api.dto.PageResponse;
import com.agrigenius.recommendation.api.dto.RecommendationResponse;
import com.agrigenius.recommendation.api.dto.RecommendationSummary;
import com.agrigenius.recommendation.application.RecommendationNotFoundException;
import com.agrigenius.recommendation.application.RecommendationService;
import com.agrigenius.recommendation.infrastructure.CropRepository;
import com.agrigenius.support.WebSliceTestConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RecommendationController.class)
@Import(WebSliceTestConfig.class)
@ActiveProfiles("test")
class RecommendationControllerWebTest {

    private static final UUID USER = UUID.randomUUID();
    private static final UUID REC_ID = UUID.fromString("8b0c2f1e-4a5d-4c3b-9e7f-1a2b3c4d5e6f");

    private static final String VALID = """
            {
              "soil": { "nitrogenKgHa": 90, "phosphorusKgHa": 42, "potassiumKgHa": 43, "ph": 6.5,
                        "soilType": "LOAMY", "moisturePct": 30 },
              "climate": { "temperatureC": 21, "humidityPct": 82, "rainfallMm": 203 },
              "season": "KHARIF", "irrigationAvailable": true, "previousCrop": "maize",
              "location": { "state": "Telangana", "district": "Warangal" }
            }
            """;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RecommendationService service;

    @MockitoBean
    CropRepository crops;

    @BeforeEach
    void knownCrops() {
        given(crops.existsById(anyString())).willAnswer(i -> List.of("maize", "rice").contains(i.<String>getArgument(0)));
    }

    private static JwtRequestPostProcessor farmer() {
        return jwt().jwt(j -> j.subject(USER.toString())).authorities(new SimpleGrantedAuthority("ROLE_FARMER"));
    }

    private static RecommendationResponse sampleResponse() {
        return new RecommendationResponse(REC_ID, "COMPLETED", Instant.parse("2026-11-17T10:15:00Z"),
                new RecommendationResponse.ModelSummary("crop-recommendation-rf", "1.0.0", "DEMO_PUBLIC_DATASET"),
                List.of("nitrogenKgHa", "phosphorusKgHa", "potassiumKgHa", "ph", "temperatureC", "humidityPct",
                        "rainfallMm"),
                List.of(new RecommendationResponse.Candidate(1, new RecommendationResponse.CropRef("rice", "Rice"), 87,
                        0.87, "HIGH",
                        List.of(new RecommendationResponse.Explanation("rainfallMm", 203, List.of(182.6, 298.6),
                                "WITHIN")),
                        new RecommendationResponse.Economics(false, "No verified market or cost data yet."))),
                List.of(), "Confirm with your local agriculture officer or KVK before planting.");
    }

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        given(service.create(eq(USER), any())).willReturn(sampleResponse());

        mockMvc.perform(post("/api/recommendations").with(farmer())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/recommendations/" + REC_ID))
                .andExpect(jsonPath("$.id").value(REC_ID.toString()))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.model.version").value("1.0.0"))
                .andExpect(jsonPath("$.model.dataLabel").value("DEMO_PUBLIC_DATASET"))
                .andExpect(jsonPath("$.featuresUsedByModel.length()").value(7))
                .andExpect(jsonPath("$.candidates[0].crop.code").value("rice"))
                .andExpect(jsonPath("$.candidates[0].suitabilityScore").value(87))
                .andExpect(jsonPath("$.candidates[0].confidence").value("HIGH"))
                .andExpect(jsonPath("$.candidates[0].explanation[0].typicalRange[0]").value(182.6))
                .andExpect(jsonPath("$.candidates[0].explanation[0].fit").value("WITHIN"))
                .andExpect(jsonPath("$.candidates[0].economics.available").value(false))
                .andExpect(jsonPath("$.advisory").isNotEmpty());
    }

    @Test
    void minimalRequestWithOnlyRequiredFieldsIsAccepted() throws Exception {
        given(service.create(eq(USER), any())).willReturn(sampleResponse());

        mockMvc.perform(post("/api/recommendations").with(farmer()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "soil": { "nitrogenKgHa": 0, "phosphorusKgHa": 0, "potassiumKgHa": 0, "ph": 3 },
                                  "climate": { "temperatureC": -5, "humidityPct": 0, "rainfallMm": 0 } }
                                """))
                .andExpect(status().isCreated());
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                Arguments.of(VALID.replace("\"nitrogenKgHa\": 90", "\"nitrogenKgHa\": -1"), "soil.nitrogenKgHa"),
                Arguments.of(VALID.replace("\"phosphorusKgHa\": 42", "\"phosphorusKgHa\": 301"), "soil.phosphorusKgHa"),
                Arguments.of(VALID.replace("\"potassiumKgHa\": 43, ", ""), "soil.potassiumKgHa"),
                Arguments.of(VALID.replace("\"ph\": 6.5", "\"ph\": 2.9"), "soil.ph"),
                Arguments.of(VALID.replace("\"ph\": 6.5", "\"ph\": 10.1"), "soil.ph"),
                Arguments.of(VALID.replace("\"soilType\": \"LOAMY\"", "\"soilType\": \"MUD\""), "soil.soilType"),
                Arguments.of(VALID.replace("\"moisturePct\": 30", "\"moisturePct\": 101"), "soil.moisturePct"),
                Arguments.of(VALID.replace("\"temperatureC\": 21", "\"temperatureC\": 56"), "climate.temperatureC"),
                Arguments.of(VALID.replace("\"temperatureC\": 21", "\"temperatureC\": -6"), "climate.temperatureC"),
                Arguments.of(VALID.replace("\"humidityPct\": 82", "\"humidityPct\": 100.5"), "climate.humidityPct"),
                Arguments.of(VALID.replace("\"rainfallMm\": 203", "\"rainfallMm\": -1"), "climate.rainfallMm"),
                Arguments.of(VALID.replace("\"rainfallMm\": 203", "\"rainfallMm\": 5001"), "climate.rainfallMm"),
                Arguments.of(VALID.replace("\"season\": \"KHARIF\"", "\"season\": \"WINTER\""), "season"),
                Arguments.of(VALID.replace("\"previousCrop\": \"maize\"", "\"previousCrop\": \"dragonfruit\""),
                        "previousCrop"),
                Arguments.of(VALID.replace("\"state\": \"Telangana\"", "\"state\": \"Telangana 123\""),
                        "location.state"),
                Arguments.of("{ \"climate\": { \"temperatureC\": 21, \"humidityPct\": 82, \"rainfallMm\": 203 } }",
                        "soil"));
    }

    @ParameterizedTest
    @MethodSource("invalidBodies")
    void invalidInputIs400WithFieldError(String body, String field) throws Exception {
        mockMvc.perform(post("/api/recommendations").with(farmer())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem(field)));
        verify(service, never()).create(any(), any());
    }

    @Test
    void malformedJsonIs400() throws Exception {
        mockMvc.perform(post("/api/recommendations").with(farmer())
                        .contentType(MediaType.APPLICATION_JSON).content("{ \"soil\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void withoutTokenIs401() throws Exception {
        mockMvc.perform(post("/api/recommendations").contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
        mockMvc.perform(get("/api/recommendations/history"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void otherRolesAre403() throws Exception {
        mockMvc.perform(post("/api/recommendations")
                        .with(jwt().jwt(j -> j.subject(USER.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(get("/api/recommendations/" + REC_ID)
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_BUYER"))))
                .andExpect(status().isForbidden());
        verify(service, never()).create(any(), any());
    }

    @Test
    void getOwnRecommendation() throws Exception {
        given(service.get(USER, REC_ID)).willReturn(sampleResponse());

        mockMvc.perform(get("/api/recommendations/" + REC_ID).with(farmer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(REC_ID.toString()))
                .andExpect(jsonPath("$.candidates[0].crop.name").value("Rice"));
    }

    @Test
    void foreignOrUnknownIdIs404() throws Exception {
        given(service.get(USER, REC_ID)).willThrow(new RecommendationNotFoundException());

        mockMvc.perform(get("/api/recommendations/" + REC_ID).with(farmer()))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void nonUuidIdIs400() throws Exception {
        mockMvc.perform(get("/api/recommendations/not-a-uuid").with(farmer()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void mlUnavailableIs503() throws Exception {
        given(service.create(eq(USER), any()))
                .willThrow(new MlServiceUnavailableException("The crop recommendation service is unavailable."));

        mockMvc.perform(post("/api/recommendations").with(farmer())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("ML_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void mlBadResponseIs502() throws Exception {
        given(service.create(eq(USER), any())).willThrow(new MlServiceBadResponseException("bad"));

        mockMvc.perform(post("/api/recommendations").with(farmer())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("ML_SERVICE_ERROR"));
    }

    @Test
    void throttledIs429WithRetryAfter() throws Exception {
        given(service.create(eq(USER), any())).willThrow(new TooManyRequestsException(Duration.ofMinutes(10)));

        mockMvc.perform(post("/api/recommendations").with(farmer())
                        .contentType(MediaType.APPLICATION_JSON).content(VALID))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "600"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    void historyUsesCallerAndClampsPaging() throws Exception {
        given(service.history(eq(USER), anyInt(), anyInt())).willReturn(new PageResponse<>(
                List.of(new RecommendationSummary(REC_ID, "COMPLETED", Instant.parse("2026-11-17T10:15:00Z"),
                        new RecommendationResponse.CropRef("rice", "Rice"), "HIGH")), 0, 50, 1, 1));

        mockMvc.perform(get("/api/recommendations/history?page=-3&size=500").with(farmer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].topCrop.code").value("rice"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(content().string(not(containsString("password"))));
        verify(service).history(USER, 0, 50);

        mockMvc.perform(get("/api/recommendations/history").with(farmer())).andExpect(status().isOk());
        verify(service).history(USER, 0, 20);
    }
}
