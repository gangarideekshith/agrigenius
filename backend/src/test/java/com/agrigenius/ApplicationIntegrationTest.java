package com.agrigenius;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrigenius.integration.ml.MlServiceClient;
import com.agrigenius.integration.ml.MlServiceException;
import com.agrigenius.support.PostgresTestcontainer;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Boots the full application against a real PostgreSQL 17 + pgvector container. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApplicationIntegrationTest implements PostgresTestcontainer {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    Flyway flyway;

    @MockitoBean
    MlServiceClient mlServiceClient;

    @Test
    void flywayAppliesMigrationsAndPgvectorIsAvailable() {
        java.util.List<String> applied = jdbcTemplate.queryForList(
                "select version from flyway_schema_history where success and version is not null order by installed_rank",
                String.class);
        assertThat(applied).containsExactly("1", "2", "3", "4", "5");

        String vectorVersion = jdbcTemplate.queryForObject(
                "select extversion from pg_extension where extname = 'vector'", String.class);
        assertThat(vectorVersion).isEqualTo("0.8.7");

        java.util.List<String> appTables = jdbcTemplate.queryForList("""
                select table_name from information_schema.tables
                where table_schema = 'public' and table_name <> 'flyway_schema_history'
                order by table_name
                """, String.class);
        assertThat(appTables).containsExactly(
                "chat_messages", "chat_sessions", "chat_usage_daily", "crop_recommendation_requests",
                "crop_recommendation_results", "crops", "farmer_profiles", "knowledge_chunks", "knowledge_documents",
                "notifications", "reminders", "users");
    }

    @Test
    void flywayCannotWipeTheDatabase() {
        assertThat(flyway.getConfiguration().isCleanDisabled()).isTrue();
    }

    @Test
    void healthIsPublicAndHidesDetails() throws Exception {
        given(mlServiceClient.health()).willThrow(new MlServiceException("down"));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$", not(hasKey("components"))))
                .andExpect(jsonPath("$", not(hasKey("details"))));
    }

    @Test
    void livenessAndReadinessProbesArePublic() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void infoIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.app.name").value("AgriGenius Backend"));
    }

    @Test
    void otherActuatorEndpointsAreNotExposed() throws Exception {
        mockMvc.perform(get("/actuator/env")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
    }

    @Test
    void apiRequiresAuthenticationAndAnswersWithProblemJson() throws Exception {
        mockMvc.perform(get("/api/anything"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/anything"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(header().string("X-Request-Id", matchesPattern("[0-9a-f-]{36}")));
    }

    @Test
    void apiDocsAreDisabledOutsideLocalProfile() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized());
    }
}
