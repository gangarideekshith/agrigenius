package com.agrigenius.config;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrigenius.common.error.GlobalExceptionHandler;
import com.agrigenius.common.web.RequestIdFilter;
import com.agrigenius.support.TestApiController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = TestApiController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class, RequestIdFilter.class})
@EnableConfigurationProperties(CorsProperties.class)
@TestPropertySource(properties = "agrigenius.cors.allowed-origins=http://localhost:4200")
class SecurityConfigTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void anonymousApiRequestGets401ProblemJson() throws Exception {
        mockMvc.perform(get("/api/test/ping"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.instance").value("/api/test/ping"))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE));
    }

    @Test
    @WithMockUser
    void authenticatedApiRequestSucceeds() throws Exception {
        mockMvc.perform(get("/api/test/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("pong"));
    }

    @Test
    void noSessionCookieIsCreated() throws Exception {
        mockMvc.perform(get("/api/test/ping"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE));
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        mockMvc.perform(get("/api/test/ping"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")));
    }

    @Test
    void corsPreflightFromAllowedOriginIsAccepted() throws Exception {
        mockMvc.perform(options("/api/test/ping")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void corsPreflightFromUnknownOriginIsRejected() throws Exception {
        mockMvc.perform(options("/api/test/ping")
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void apiDocsAreNotPublicWhenDocsAreDisabled() throws Exception {
        mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
    }

    @Test
    void safeIncomingRequestIdIsEchoed() throws Exception {
        mockMvc.perform(get("/api/test/ping").header(RequestIdFilter.HEADER, "client-abc-123"))
                .andExpect(header().string(RequestIdFilter.HEADER, "client-abc-123"))
                .andExpect(jsonPath("$.requestId").value("client-abc-123"));
    }

    @Test
    void unsafeIncomingRequestIdIsReplaced() throws Exception {
        mockMvc.perform(get("/api/test/ping").header(RequestIdFilter.HEADER, "bad\nvalue<script>"))
                .andExpect(header().string(RequestIdFilter.HEADER, not(containsString("script"))))
                .andExpect(header().string(RequestIdFilter.HEADER, org.hamcrest.Matchers.matchesPattern("[0-9a-f-]{36}")));
    }
}
