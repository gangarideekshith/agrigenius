package com.agrigenius.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrigenius.integration.ml.MlServiceClient;
import com.agrigenius.integration.ml.crop.CropModelClient;
import com.agrigenius.integration.ml.crop.CropPrediction;
import com.agrigenius.integration.ml.crop.CropPrediction.Candidate;
import com.agrigenius.integration.ml.crop.CropPrediction.FeatureExplanation;
import com.agrigenius.integration.ml.crop.CropPrediction.ModelRef;
import com.agrigenius.integration.ml.crop.CropPrediction.Warning;
import com.agrigenius.integration.ml.crop.MlServiceUnavailableException;
import com.agrigenius.support.PostgresTestcontainer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/**
 * End-to-end through the real security chain, JPA mapping (incl. JSONB) and PostgreSQL 17 + V3,
 * with the ML service replaced by a stub {@link CropModelClient}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecommendationIntegrationTest implements PostgresTestcontainer {

    private static final String PASSWORD = "Kh3t-Safe-pass";
    private static final String BODY = """
            {"soil":{"nitrogenKgHa":90,"phosphorusKgHa":42,"potassiumKgHa":43,"ph":6.5,"soilType":"LOAMY","moisturePct":30},
             "climate":{"temperatureC":21,"humidityPct":82,"rainfallMm":203},
             "season":"KHARIF","irrigationAvailable":true,"previousCrop":"maize",
             "location":{"state":"Telangana","district":"Warangal"}}
            """;

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;

    @MockitoBean CropModelClient cropModel;
    @MockitoBean MlServiceClient mlServiceClient;

    @Test
    void recommendationIsPersistedWithInputsResultsAndModelTraceability() throws Exception {
        given(cropModel.predict(any(), any(), anyInt())).willReturn(prediction(0.87, List.of()));
        String token = registerAndLogin();

        String location = mockMvc.perform(post("/api/recommendations").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/recommendations/")))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.candidates.length()").value(5))
                .andExpect(jsonPath("$.candidates[0].crop.code").value("rice"))
                .andExpect(jsonPath("$.candidates[0].confidence").value("HIGH"))
                .andExpect(jsonPath("$.candidates[0].suitabilityScore").value(87))
                .andExpect(jsonPath("$.candidates[0].economics.available").value(false))
                .andExpect(jsonPath("$.model.version").value("1.0.0"))
                .andReturn().getResponse().getHeader("Location");
        UUID id = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));

        Map<String, Object> row = jdbc.queryForMap(
                "select status, nitrogen_kg_ha, rainfall_mm, season, previous_crop_code, model_name, model_version,"
                        + " model_sha256, completed_at from crop_recommendation_requests where id = ?", id);
        assertThat(row.get("status")).isEqualTo("COMPLETED");
        assertThat(((Number) row.get("rainfall_mm")).doubleValue()).isEqualTo(203.0);
        assertThat(row.get("season")).isEqualTo("KHARIF");
        assertThat(row.get("previous_crop_code")).isEqualTo("maize");
        assertThat(row.get("model_sha256")).isEqualTo("a".repeat(64));
        assertThat(row.get("completed_at")).isNotNull();

        Integer results = jdbc.queryForObject(
                "select count(*) from crop_recommendation_results where request_id = ?", Integer.class, id);
        assertThat(results).isEqualTo(5);
        String explanation = jdbc.queryForObject(
                "select explanation::text from crop_recommendation_results where request_id = ? and rank = 1",
                String.class, id);
        assertThat(json.readTree(explanation).isArray()).isTrue();

        mockMvc.perform(get(location).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.candidates[0].crop.code").value("rice"));
    }

    @Test
    void lowConfidenceAndOutOfRangeWarningsAreStored() throws Exception {
        given(cropModel.predict(any(), any(), anyInt()))
                .willReturn(prediction(0.24, List.of(new Warning("INPUT_OUTSIDE_TRAINING_RANGE", "rainfall_mm"))));
        String token = registerAndLogin();

        String location = mockMvc.perform(post("/api/recommendations").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("LOW_CONFIDENCE"))
                .andExpect(jsonPath("$.candidates[0].confidence").value("LOW"))
                .andExpect(jsonPath("$.warnings[0].code").value("INPUT_OUTSIDE_TRAINING_RANGE"))
                .andReturn().getResponse().getHeader("Location");
        UUID id = UUID.fromString(location.substring(location.lastIndexOf('/') + 1));

        String warnings = jdbc.queryForObject(
                "select warnings::text from crop_recommendation_requests where id = ?", String.class, id);
        assertThat(warnings).contains("INPUT_OUTSIDE_TRAINING_RANGE");
    }

    @Test
    void mlOutageIsRecordedAsFailedAndReturns503() throws Exception {
        given(cropModel.predict(any(), any(), anyInt())).willThrow(new MlServiceUnavailableException("down"));
        String token = registerAndLogin();
        UUID userId = userIdOf(token);

        mockMvc.perform(post("/api/recommendations").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ML_SERVICE_UNAVAILABLE"));

        Map<String, Object> row = jdbc.queryForMap(
                "select status, failure_code from crop_recommendation_requests where user_id = ?", userId);
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("failure_code")).isEqualTo("ML_SERVICE_UNAVAILABLE");
        assertThat(jdbc.queryForObject("select count(*) from crop_recommendation_results r join crop_recommendation_requests q"
                + " on q.id = r.request_id where q.user_id = ?", Integer.class, userId)).isZero();
    }

    @Test
    void farmersSeeOnlyTheirOwnRecommendations() throws Exception {
        given(cropModel.predict(any(), any(), anyInt())).willReturn(prediction(0.87, List.of()));
        String alice = registerAndLogin();
        String bob = registerAndLogin();

        String location = mockMvc.perform(post("/api/recommendations").header("Authorization", "Bearer " + alice)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated()).andReturn().getResponse().getHeader("Location");
        mockMvc.perform(post("/api/recommendations").header("Authorization", "Bearer " + alice)
                .contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isCreated());

        mockMvc.perform(get(location).header("Authorization", "Bearer " + bob))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(get("/api/recommendations/history").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items.length()").value(2));
        mockMvc.perform(get("/api/recommendations/history").header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));
    }

    @Test
    void unknownPreviousCropIsRejectedWithoutCallingTheModel() throws Exception {
        String token = registerAndLogin();
        mockMvc.perform(post("/api/recommendations").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(BODY.replace("\"maize\"", "\"dragonfruit\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("previousCrop"));
    }

    @Test
    void appDatabaseRoleHasDataOnlyAccessToRecommendationTables() throws SQLException {
        try (Connection c = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "agrigenius_app_test",
                "test-only-app-role-password")) {
            c.setAutoCommit(false);
            c.createStatement().executeQuery("select count(*) from crop_recommendation_requests").close();
            c.createStatement().executeQuery("select count(*) from crops").close();
            assertThatThrownBy(() -> c.createStatement().execute("drop table crop_recommendation_results"))
                    .isInstanceOf(SQLException.class)
                    .satisfies(e -> assertThat(((SQLException) e).getSQLState()).isEqualTo("42501"));
            c.rollback();
        }
    }

    private static CropPrediction prediction(double topProbability, List<Warning> warnings) {
        double rest = (1 - topProbability) / 4;
        List<String> crops = List.of("rice", "jute", "coconut", "papaya", "maize");
        List<Candidate> candidates = new java.util.ArrayList<>();
        for (int i = 0; i < crops.size(); i++) {
            candidates.add(new Candidate(crops.get(i), i == 0 ? topProbability : rest,
                    List.of(new FeatureExplanation("rainfall_mm", 203.0, 182.6, 298.6))));
        }
        return new CropPrediction(new ModelRef("crop-recommendation-rf", "1.0.0", "a".repeat(64), "DEMO_PUBLIC_DATASET"),
                candidates, warnings, 4);
    }

    private String registerAndLogin() throws Exception {
        String mobile = "9" + String.format("%09d", ThreadLocalRandom.current().nextLong(1_000_000_000L));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"fullName\":\"Ravi Kumar\",\"mobileNumber\":\"" + mobile + "\",\"password\":\"" + PASSWORD
                                + "\",\"preferredLanguage\":\"te\"}"))
                .andExpect(status().isCreated());
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobileNumber\":\"" + mobile + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private UUID userIdOf(String token) {
        String payload = token.split("\\.")[1];
        String decoded = new String(java.util.Base64.getUrlDecoder().decode(payload), java.nio.charset.StandardCharsets.UTF_8);
        return UUID.fromString(json.readTree(decoded).get("sub").asText());
    }
}
