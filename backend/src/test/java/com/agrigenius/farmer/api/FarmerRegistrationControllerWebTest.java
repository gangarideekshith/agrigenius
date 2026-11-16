package com.agrigenius.farmer.api;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrigenius.common.error.TooManyRequestsException;
import com.agrigenius.farmer.api.dto.FarmerProfileResponse;
import com.agrigenius.farmer.api.dto.RegisterFarmerRequest;
import com.agrigenius.farmer.application.FarmerRegistrationService;
import com.agrigenius.identity.application.AuthThrottle;
import com.agrigenius.support.WebSliceTestConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(FarmerRegistrationController.class)
@Import(WebSliceTestConfig.class)
@ActiveProfiles("test")
class FarmerRegistrationControllerWebTest {

    private static final String VALID = """
            {"fullName":"Ravi Kumar","mobileNumber":"9876543210","password":"s3cure-pass",
             "preferredLanguage":"te","email":"ravi@example.com","state":"Telangana"}
            """;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    FarmerRegistrationService registrationService;

    @MockitoBean
    AuthThrottle throttle;

    private ResultActions register(String body) throws Exception {
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void givenRegistrationSucceeds() {
        given(registrationService.register(any())).willReturn(new FarmerProfileResponse(UUID.randomUUID(),
                "Ravi Kumar", "+919876543210", "ravi@example.com", "te", "Telangana", null, null, null,
                "FARMER", Instant.now()));
    }

    @Test
    void validRegistrationReturns201WithLocation() throws Exception {
        givenRegistrationSucceeds();

        register(VALID)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/farmers/me"))
                .andExpect(jsonPath("$.role").value("FARMER"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(content().string(Matchers.not(Matchers.containsString("s3cure-pass"))));
    }

    @Test
    void garbageBearerTokenIsIgnoredOnRegister() throws Exception {
        givenRegistrationSucceeds();

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(VALID)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isCreated());
    }

    @Test
    void roleInBodyIsIgnored() throws Exception {
        givenRegistrationSucceeds();

        register(VALID.replace("\"state\":\"Telangana\"", "\"state\":\"Telangana\",\"role\":\"ADMIN\""))
                .andExpect(status().isCreated());

        ArgumentCaptor<RegisterFarmerRequest> captor = ArgumentCaptor.forClass(RegisterFarmerRequest.class);
        verify(registrationService).register(captor.capture());
        org.assertj.core.api.Assertions.assertThat(
                java.util.Arrays.stream(RegisterFarmerRequest.class.getRecordComponents()).map(c -> c.getName()))
                .doesNotContain("role");
    }

    private void expectFieldError(String body, String field) throws Exception {
        register(body)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", hasItem(field)));
        verify(registrationService, never()).register(any());
    }

    @Test
    void blankFullNameIsRejected() throws Exception {
        expectFieldError(VALID.replace("Ravi Kumar", "  "), "fullName");
    }

    @Test
    void fullNameWithDigitsIsRejected() throws Exception {
        expectFieldError(VALID.replace("Ravi Kumar", "Ravi 123"), "fullName");
    }

    @Test
    void invalidMobileIsRejected() throws Exception {
        expectFieldError(VALID.replace("9876543210", "5876543210"), "mobileNumber");
    }

    @Test
    void weakPasswordIsRejected() throws Exception {
        expectFieldError(VALID.replace("s3cure-pass", "password"), "password");
    }

    @Test
    void passwordEqualToMobileIsRejected() throws Exception {
        expectFieldError(VALID.replace("s3cure-pass", "9876543210").replace("\"password\"", "\"password\""), "password");
    }

    @Test
    void unsupportedLanguageIsRejected() throws Exception {
        expectFieldError(VALID.replace("\"te\"", "\"fr\""), "preferredLanguage");
    }

    @Test
    void badEmailIsRejected() throws Exception {
        expectFieldError(VALID.replace("ravi@example.com", "not-an-email"), "email");
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        register("{not json")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void throttledRequestReturns429WithRetryAfter() throws Exception {
        willThrow(new TooManyRequestsException(Duration.ofSeconds(90))).given(throttle).checkIpAllowed(any());

        register(VALID)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "90"));
        verify(registrationService, never()).register(any());
    }
}
