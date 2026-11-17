package com.agrigenius.auth;

import com.agrigenius.support.PostgresTestcontainer;
import static com.agrigenius.auth.AuthTestSupport.PASSWORD;
import static com.agrigenius.auth.AuthTestSupport.login;
import static com.agrigenius.auth.AuthTestSupport.randomMobile;
import static com.agrigenius.auth.AuthTestSupport.register;
import static com.agrigenius.auth.AuthTestSupport.registerJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrigenius.integration.ml.MlServiceClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** End-to-end registration, login and profile flow against a real PostgreSQL container. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest implements PostgresTestcontainer {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    JsonMapper jsonMapper;

    @MockitoBean
    MlServiceClient mlServiceClient;

    @Test
    void registerLoginAndReadOwnProfile() throws Exception {
        String mobile = randomMobile();

        MvcResult registered = mockMvc.perform(register(registerJson(mobile, PASSWORD, "Ravi@Example.com", null)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/farmers/me"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.fullName").value("Ravi Kumar"))
                .andExpect(jsonPath("$.mobileNumber").value("+91" + mobile))
                .andExpect(jsonPath("$.email").value("ravi@example.com"))
                .andExpect(jsonPath("$.preferredLanguage").value("te"))
                .andExpect(jsonPath("$.role").value("FARMER"))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString(PASSWORD))))
                .andReturn();
        String id = json(registered).get("id").asString();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "select password_hash, role, status, last_login_at from users where id = ?::uuid", id);
        assertThat((String) row.get("password_hash")).startsWith("{bcrypt}$2").isNotEqualTo(PASSWORD);
        assertThat(row.get("role")).isEqualTo("FARMER");
        assertThat(row.get("status")).isEqualTo("ACTIVE");
        assertThat(row.get("last_login_at")).isNull();
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from farmer_profiles where user_id = ?::uuid", Integer.class, id)).isEqualTo(1);

        MvcResult loggedIn = mockMvc.perform(login(mobile, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.user.id").value(id))
                .andExpect(jsonPath("$.user.role").value("FARMER"))
                .andReturn();
        String token = json(loggedIn).get("accessToken").asString();
        assertThat(token).isNotBlank();
        assertThat(jdbcTemplate.queryForObject(
                "select last_login_at from users where id = ?::uuid", Object.class, id)).isNotNull();

        mockMvc.perform(get("/api/farmers/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.mobileNumber").value("+91" + mobile))
                .andExpect(jsonPath("$.district").value("Warangal"))
                .andExpect(content().string(not(containsString("password"))));
    }

    @Test
    void roleInRequestBodyIsIgnored() throws Exception {
        String mobile = randomMobile();
        MvcResult result = mockMvc.perform(register(registerJson(mobile, PASSWORD, null,
                        "\"role\":\"ADMIN\",\"status\":\"DISABLED\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("FARMER"))
                .andReturn();
        String id = json(result).get("id").asString();
        assertThat(jdbcTemplate.queryForObject("select role from users where id = ?::uuid", String.class, id))
                .isEqualTo("FARMER");
        assertThat(jdbcTemplate.queryForObject("select status from users where id = ?::uuid", String.class, id))
                .isEqualTo("ACTIVE");
    }

    @Test
    void duplicateMobileIsRejected() throws Exception {
        String mobile = randomMobile();
        mockMvc.perform(register(registerJson(mobile, PASSWORD, null, null))).andExpect(status().isCreated());
        mockMvc.perform(register(registerJson("+91 " + mobile, PASSWORD, null, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void duplicateEmailInDifferentCaseIsRejected() throws Exception {
        String email = "farmer" + randomMobile() + "@example.com";
        mockMvc.perform(register(registerJson(randomMobile(), PASSWORD, email, null))).andExpect(status().isCreated());
        mockMvc.perform(register(registerJson(randomMobile(), PASSWORD, email.toUpperCase(), null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_ALREADY_EXISTS"));
    }

    @Test
    void concurrentRegistrationsOfSameMobileCreateExactlyOneAccount() throws Exception {
        String mobile = randomMobile();
        String body = registerJson(mobile, PASSWORD, null, null);
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Callable<Integer> call = () -> {
                    start.await();
                    return mockMvc.perform(register(body)).andReturn().getResponse().getStatus();
                };
                futures.add(pool.submit(call));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) {
                statuses.add(f.get());
            }
            assertThat(statuses).filteredOn(s -> s == 201).hasSize(1);
            assertThat(statuses).filteredOn(s -> s == 409).hasSize(threads - 1);
            assertThat(jdbcTemplate.queryForObject(
                    "select count(*) from users where mobile_number = ?", Integer.class, "+91" + mobile)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void loginFailuresAreIndistinguishable() throws Exception {
        String mobile = randomMobile();
        mockMvc.perform(register(registerJson(mobile, PASSWORD, null, null))).andExpect(status().isCreated());
        String disabledMobile = randomMobile();
        mockMvc.perform(register(registerJson(disabledMobile, PASSWORD, null, null))).andExpect(status().isCreated());
        jdbcTemplate.update("update users set status = 'DISABLED' where mobile_number = ?", "+91" + disabledMobile);

        JsonNode wrongPassword = json(mockMvc.perform(login(mobile, "Wrong-pass-1"))
                .andExpect(status().isUnauthorized()).andReturn());
        JsonNode unknownNumber = json(mockMvc.perform(login(randomMobile(), PASSWORD))
                .andExpect(status().isUnauthorized()).andReturn());
        JsonNode disabled = json(mockMvc.perform(login(disabledMobile, PASSWORD))
                .andExpect(status().isUnauthorized()).andReturn());

        for (JsonNode body : List.of(wrongPassword, unknownNumber, disabled)) {
            assertThat(body.get("code").asString()).isEqualTo("INVALID_CREDENTIALS");
            assertThat(body.get("title").asString()).isEqualTo(wrongPassword.get("title").asString());
            assertThat(body.get("detail").asString()).isEqualTo(wrongPassword.get("detail").asString());
            assertThat(body.get("status").asInt()).isEqualTo(401);
        }
    }

    @Test
    void tokenOfDisabledAccountIsRejected() throws Exception {
        String mobile = randomMobile();
        mockMvc.perform(register(registerJson(mobile, PASSWORD, null, null))).andExpect(status().isCreated());
        String token = json(mockMvc.perform(login(mobile, PASSWORD)).andExpect(status().isOk()).andReturn())
                .get("accessToken").asString();

        jdbcTemplate.update("update users set status = 'DISABLED' where mobile_number = ?", "+91" + mobile);

        mockMvc.perform(get("/api/farmers/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void staleAuthorizationHeaderDoesNotBreakLogin() throws Exception {
        String mobile = randomMobile();
        mockMvc.perform(register(registerJson(mobile, PASSWORD, null, null))).andExpect(status().isCreated());

        mockMvc.perform(login(mobile, PASSWORD).header(HttpHeaders.AUTHORIZATION, "Bearer not.a.valid.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
        mockMvc.perform(register(registerJson(randomMobile(), PASSWORD, null, null))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired.garbage.token"))
                .andExpect(status().isCreated());
    }

    private JsonNode json(MvcResult result) throws Exception {
        return jsonMapper.readTree(result.getResponse().getContentAsString());
    }
}
