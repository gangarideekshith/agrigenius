package com.agrigenius.auth;

import com.agrigenius.support.PostgresTestcontainer;
import static com.agrigenius.auth.AuthTestSupport.PASSWORD;
import static com.agrigenius.auth.AuthTestSupport.login;
import static com.agrigenius.auth.AuthTestSupport.randomMobile;
import static com.agrigenius.auth.AuthTestSupport.register;
import static com.agrigenius.auth.AuthTestSupport.registerJson;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrigenius.integration.ml.MlServiceClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** Passwords, hashes, tokens and full mobile numbers never reach the logs. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AuthLogSafetyIntegrationTest implements PostgresTestcontainer {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JsonMapper jsonMapper;

    @MockitoBean
    MlServiceClient mlServiceClient;

    @Test
    void authFlowLogsNoSecretsOrFullMobileNumbers(CapturedOutput output) throws Exception {
        String mobile = randomMobile();
        String wrongPassword = "Wrong-secret-77";

        mockMvc.perform(register(registerJson(mobile, PASSWORD, null, null))).andExpect(status().isCreated());
        mockMvc.perform(login(mobile, wrongPassword)).andExpect(status().isUnauthorized());
        String token = jsonMapper.readTree(mockMvc.perform(login(mobile, PASSWORD)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("accessToken").asString();
        mockMvc.perform(get("/api/farmers/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());

        String logs = output.getAll();
        assertThat(logs)
                .doesNotContain(PASSWORD)
                .doesNotContain(wrongPassword)
                .doesNotContain(mobile)
                .doesNotContain(token)
                .doesNotContain(token.split("\\.")[2])
                .doesNotContain("{bcrypt}")
                .contains("******" + mobile.substring(6));
    }
}
