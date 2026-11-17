package com.agrigenius.chat.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrigenius.integration.ml.MlServiceClient;
import com.agrigenius.support.PostgresTestcontainer;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

/** Default configuration (no provider): the app starts, chat answers 503 CHAT_DISABLED and stores nothing. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatDisabledIntegrationTest implements PostgresTestcontainer {

    @Autowired MockMvc mockMvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired ObjectProvider<ChatModel> chatModel;

    @MockitoBean MlServiceClient mlServiceClient;

    @Test
    void chatIsDisabledWithoutProviderAndNothingIsStored() throws Exception {
        assertThat(chatModel.getIfAvailable()).as("no provider configured by default").isNull();

        String mobile = "9" + String.format("%09d", ThreadLocalRandom.current().nextLong(1_000_000_000L));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(
                "{\"fullName\":\"Ravi Kumar\",\"mobileNumber\":\"" + mobile
                        + "\",\"password\":\"Kh3t-Safe-pass\",\"preferredLanguage\":\"en\"}")).andExpect(status().isCreated());
        String token = json.readTree(mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mobileNumber\":\"" + mobile + "\",\"password\":\"Kh3t-Safe-pass\"}"))
                .andReturn().getResponse().getContentAsString()).get("accessToken").asText();
        String sessionId = json.readTree(mockMvc.perform(post("/api/chat/sessions").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"language\":\"en\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/chat/sessions/" + sessionId + "/messages").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"Hello\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CHAT_DISABLED"));

        assertThat(jdbc.queryForObject("select count(*) from chat_messages where session_id = ?", Integer.class,
                UUID.fromString(sessionId))).isZero();
    }
}
