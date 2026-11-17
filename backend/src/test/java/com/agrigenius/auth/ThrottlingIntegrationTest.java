package com.agrigenius.auth;

import com.agrigenius.support.PostgresTestcontainer;
import static com.agrigenius.auth.AuthTestSupport.PASSWORD;
import static com.agrigenius.auth.AuthTestSupport.login;
import static com.agrigenius.auth.AuthTestSupport.randomMobile;
import static com.agrigenius.auth.AuthTestSupport.register;
import static com.agrigenius.auth.AuthTestSupport.registerJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrigenius.integration.ml.MlServiceClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Brute-force protection: repeated failures and floods get 429 with Retry-After (decision D5). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "agrigenius.security.throttling.failed-logins-per-mobile=3",
        "agrigenius.security.throttling.requests-per-ip=1000"})
class ThrottlingIntegrationTest implements PostgresTestcontainer {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    MlServiceClient mlServiceClient;

    @Test
    void repeatedFailedLoginsBlockTheNumberEvenForTheRightPassword() throws Exception {
        String mobile = randomMobile();
        mockMvc.perform(register(registerJson(mobile, PASSWORD, null, null))).andExpect(status().isCreated());

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(login(mobile, "Wrong-pass-" + i)).andExpect(status().isUnauthorized());
        }

        MvcResult blocked = mockMvc.perform(login(mobile, PASSWORD))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andReturn();
        assertThat(Long.parseLong(blocked.getResponse().getHeader(HttpHeaders.RETRY_AFTER))).isPositive();
    }

    @Test
    void successfulLoginResetsTheFailureCount() throws Exception {
        String mobile = randomMobile();
        mockMvc.perform(register(registerJson(mobile, PASSWORD, null, null))).andExpect(status().isCreated());

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(login(mobile, "Wrong-pass-" + i)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login(mobile, PASSWORD)).andExpect(status().isOk());
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(login(mobile, "Wrong-pass-again-" + i)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login(mobile, PASSWORD)).andExpect(status().isOk());
    }

    @Test
    void otherNumbersAreNotAffected() throws Exception {
        String blockedMobile = randomMobile();
        String otherMobile = randomMobile();
        mockMvc.perform(register(registerJson(blockedMobile, PASSWORD, null, null))).andExpect(status().isCreated());
        mockMvc.perform(register(registerJson(otherMobile, PASSWORD, null, null))).andExpect(status().isCreated());

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(login(blockedMobile, "Wrong-pass-" + i)).andExpect(status().isUnauthorized());
        }
        mockMvc.perform(login(blockedMobile, PASSWORD)).andExpect(status().isTooManyRequests());
        mockMvc.perform(login(otherMobile, PASSWORD)).andExpect(status().isOk());
    }
}
