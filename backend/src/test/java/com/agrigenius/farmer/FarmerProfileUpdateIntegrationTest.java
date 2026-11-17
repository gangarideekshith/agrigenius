package com.agrigenius.farmer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrigenius.integration.ml.MlServiceClient;
import com.agrigenius.support.PostgresTestcontainer;
import java.util.Map;
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

/** PUT /api/farmers/me end to end: owner-only, validated, mobile number immutable, email unique. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FarmerProfileUpdateIntegrationTest implements PostgresTestcontainer {

    private static final String PASSWORD = "Kh3t-Safe-pass";

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @MockitoBean MlServiceClient mlServiceClient;

    @Test
    void farmerUpdatesOwnProfileAndChangesArePersisted() throws Exception {
        String[] farmer = registerAndLogin(null);
        String body = json.writeValueAsString(Map.of(
                "fullName", "  Ravi Kumar Reddy ", "preferredLanguage", "TE", "email", "Ravi@Example.ORG",
                "state", "Telangana", "district", "Warangal", "village", "Hanamkonda", "addressLine", "Near the tank",
                "mobileNumber", "9000000000", "role", "ADMIN"));

        mockMvc.perform(put("/api/farmers/me").header("Authorization", "Bearer " + farmer[1])
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Ravi Kumar Reddy"))
                .andExpect(jsonPath("$.preferredLanguage").value("te"))
                .andExpect(jsonPath("$.email").value("ravi@example.org"))
                .andExpect(jsonPath("$.addressLine").value("Near the tank"))
                .andExpect(jsonPath("$.mobileNumber").value("+91" + farmer[0]))
                .andExpect(jsonPath("$.role").value("FARMER"));

        mockMvc.perform(get("/api/farmers/me").header("Authorization", "Bearer " + farmer[1]))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.village").value("Hanamkonda"))
                .andExpect(jsonPath("$.email").value("ravi@example.org"));
        assertThat(jdbc.queryForObject("select role from users where mobile_number = ?", String.class, "+91" + farmer[0]))
                .isEqualTo("FARMER");
    }

    @Test
    void omittedOptionalFieldsAreCleared() throws Exception {
        String[] farmer = registerAndLogin("clear-" + ThreadLocalRandom.current().nextInt(1_000_000) + "@example.org");
        mockMvc.perform(put("/api/farmers/me").header("Authorization", "Bearer " + farmer[1])
                        .contentType(MediaType.APPLICATION_JSON).content("{\"fullName\":\"Ravi\",\"preferredLanguage\":\"en\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").isEmpty())
                .andExpect(jsonPath("$.state").isEmpty());
    }

    @Test
    void invalidInputReturnsFieldErrors() throws Exception {
        String[] farmer = registerAndLogin(null);
        mockMvc.perform(put("/api/farmers/me").header("Authorization", "Bearer " + farmer[1])
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"R1\",\"preferredLanguage\":\"fr\",\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void emailAlreadyUsedByAnotherAccountIsRejected() throws Exception {
        String taken = "taken-" + ThreadLocalRandom.current().nextInt(1_000_000) + "@example.org";
        registerAndLogin(taken);
        String[] other = registerAndLogin(null);
        mockMvc.perform(put("/api/farmers/me").header("Authorization", "Bearer " + other[1])
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Other Farmer\",\"preferredLanguage\":\"en\",\"email\":\"" + taken.toUpperCase() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_EXISTS"));
    }

    @Test
    void updateRequiresAuthentication() throws Exception {
        mockMvc.perform(put("/api/farmers/me").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Ravi\",\"preferredLanguage\":\"en\"}"))
                .andExpect(status().isUnauthorized());
    }

    /** Returns {mobile, accessToken}. */
    private String[] registerAndLogin(String email) throws Exception {
        String mobile = "9" + String.format("%09d", ThreadLocalRandom.current().nextLong(1_000_000_000L));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(
                        "{\"fullName\":\"Ravi Kumar\",\"mobileNumber\":\"" + mobile + "\",\"password\":\"" + PASSWORD
                                + "\",\"preferredLanguage\":\"te\"" + (email == null ? "" : ",\"email\":\"" + email + "\"") + "}"))
                .andExpect(status().isCreated());
        String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobileNumber\":\"" + mobile + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new String[] {mobile, json.readTree(body).get("accessToken").asText()};
    }
}
