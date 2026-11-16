package com.agrigenius.auth;

import com.agrigenius.support.PostgresTestcontainer;
import static com.agrigenius.auth.AuthTestSupport.PASSWORD;
import static com.agrigenius.auth.AuthTestSupport.login;
import static com.agrigenius.auth.AuthTestSupport.randomMobile;
import static com.agrigenius.auth.AuthTestSupport.register;
import static com.agrigenius.auth.AuthTestSupport.registerJson;
import static com.agrigenius.auth.AuthTestSupport.sign;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrigenius.identity.config.JwtProperties;
import com.agrigenius.integration.ml.MlServiceClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.json.JsonMapper;

/** Token verification through the real security chain: every forged or invalid token is rejected. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TokenSecurityIntegrationTest implements PostgresTestcontainer {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtProperties jwt;

    @Autowired
    JsonMapper jsonMapper;

    @MockitoBean
    MlServiceClient mlServiceClient;

    UUID farmerId;
    String validToken;

    @BeforeEach
    void registerFarmer() throws Exception {
        String mobile = randomMobile();
        String body = mockMvc.perform(register(registerJson(mobile, PASSWORD, null, null)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        farmerId = UUID.fromString(jsonMapper.readTree(body).get("id").asString());
        validToken = jsonMapper.readTree(mockMvc.perform(login(mobile, PASSWORD)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get("accessToken").asString();
    }

    @Test
    void validTokenIsAccepted() throws Exception {
        me(validToken).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(farmerId.toString()));
    }

    @Test
    void missingTokenGives401WithBearerChallenge() throws Exception {
        mockMvc.perform(get("/api/farmers/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    void tamperedPayloadIsRejected() throws Exception {
        String[] parts = validToken.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8)
                .replace("\"FARMER\"", "\"ADMIN\"");
        String forged = parts[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + parts[2];
        expectUnauthorized(me(forged));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        byte[] otherKey = "another-32-byte-key-for-testing!!-xyz".getBytes(StandardCharsets.UTF_8);
        Instant now = Instant.now();
        expectUnauthorized(me(sign(otherKey, jwt.issuer(), jwt.audience(), farmerId, List.of("FARMER"),
                now, now.plusSeconds(600))));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        Instant issued = Instant.now().minusSeconds(3600);
        expectUnauthorized(me(sign(jwt.secretBytes(), jwt.issuer(), jwt.audience(), farmerId, List.of("FARMER"),
                issued, Instant.now().minusSeconds(120))));
    }

    @Test
    void wrongIssuerIsRejected() throws Exception {
        Instant now = Instant.now();
        expectUnauthorized(me(sign(jwt.secretBytes(), "someone-else", jwt.audience(), farmerId, List.of("FARMER"),
                now, now.plusSeconds(600))));
    }

    @Test
    void wrongAudienceIsRejected() throws Exception {
        Instant now = Instant.now();
        expectUnauthorized(me(sign(jwt.secretBytes(), jwt.issuer(), "other-api", farmerId, List.of("FARMER"),
                now, now.plusSeconds(600))));
    }

    @Test
    void validTokenForAnotherRoleIsForbidden() throws Exception {
        Instant now = Instant.now();
        me(sign(jwt.secretBytes(), jwt.issuer(), jwt.audience(), farmerId, List.of("ADMIN"), now,
                now.plusSeconds(600)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void algNoneTokenIsRejected() throws Exception {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        long now = Instant.now().getEpochSecond();
        String header = b64.encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = b64.encodeToString(("{\"iss\":\"" + jwt.issuer() + "\",\"aud\":[\"" + jwt.audience()
                + "\"],\"sub\":\"" + farmerId + "\",\"roles\":[\"FARMER\"],\"iat\":" + now + ",\"nbf\":" + now
                + ",\"exp\":" + (now + 600) + "}").getBytes(StandardCharsets.UTF_8));
        expectUnauthorized(me(header + "." + payload + "."));
    }

    private ResultActions me(String token) throws Exception {
        return mockMvc.perform(get("/api/farmers/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static void expectUnauthorized(ResultActions actions) throws Exception {
        actions.andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }
}
