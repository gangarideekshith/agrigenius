package com.agrigenius.identity.api;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrigenius.identity.application.AccessTokenService;
import com.agrigenius.identity.application.AccessTokenService.IssuedToken;
import com.agrigenius.identity.application.AuthThrottle;
import com.agrigenius.identity.application.UserAccountService;
import com.agrigenius.identity.application.UserAccountService.AccountView;
import com.agrigenius.identity.domain.Role;
import com.agrigenius.support.WebSliceTestConfig;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@WebMvcTest(AuthController.class)
@Import(WebSliceTestConfig.class)
@ActiveProfiles("test")
class AuthControllerWebTest {

    private static final String BODY = "{\"mobileNumber\":\"9876543210\",\"password\":\"s3cure-pass\"}";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AuthenticationManager authenticationManager;

    @MockitoBean
    UserAccountService accounts;

    @MockitoBean
    AccessTokenService tokens;

    @MockitoBean
    AuthThrottle throttle;

    private ResultActions login(String body) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void successfulLoginReturnsToken() throws Exception {
        UUID id = UUID.randomUUID();
        given(authenticationManager.authenticate(any()))
                .willReturn(UsernamePasswordAuthenticationToken.authenticated(id.toString(), null, java.util.List.of()));
        given(accounts.findActiveById(id))
                .willReturn(Optional.of(new AccountView(id, "+919876543210", null, Role.FARMER, true, Instant.now())));
        given(tokens.issue(id, Role.FARMER)).willReturn(new IssuedToken("header.payload.sig", Duration.ofMinutes(30)));

        login(BODY)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("header.payload.sig"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.user.id").value(id.toString()))
                .andExpect(jsonPath("$.user.role").value("FARMER"))
                .andExpect(content().string(not(containsString("password"))));
        verify(throttle).recordLoginSuccess("+919876543210");
        verify(accounts).recordSuccessfulLogin(id);
    }

    @Test
    void badCredentialsReturnGeneric401() throws Exception {
        given(authenticationManager.authenticate(any())).willThrow(new BadCredentialsException("Bad credentials"));

        login(BODY)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Invalid mobile number or password."))
                .andExpect(content().string(not(containsString("s3cure-pass"))));
        verify(throttle).recordLoginFailure("+919876543210");
        verify(tokens, never()).issue(any(), any());
    }

    @Test
    void inactiveAccountGetsSameGeneric401() throws Exception {
        UUID id = UUID.randomUUID();
        given(authenticationManager.authenticate(any()))
                .willReturn(UsernamePasswordAuthenticationToken.authenticated(id.toString(), null, java.util.List.of()));
        given(accounts.findActiveById(id)).willReturn(Optional.empty());

        login(BODY)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("Invalid mobile number or password."));
        verify(tokens, never()).issue(any(), any());
    }

    @Test
    void invalidMobileFormatReturns400() throws Exception {
        login("{\"mobileNumber\":\"12345\",\"password\":\"s3cure-pass\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verify(authenticationManager, never()).authenticate(any());
    }
}
