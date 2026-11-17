package com.agrigenius.farmer.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.agrigenius.common.error.AuthenticationRequiredException;
import com.agrigenius.farmer.api.dto.FarmerProfileResponse;
import com.agrigenius.farmer.application.FarmerProfileService;
import com.agrigenius.support.WebSliceTestConfig;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(FarmerController.class)
@Import(WebSliceTestConfig.class)
@ActiveProfiles("test")
class FarmerControllerWebTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    FarmerProfileService profileService;

    @Test
    void noTokenReturns401WithBearerChallenge() throws Exception {
        mockMvc.perform(get("/api/farmers/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
        verify(profileService, never()).getForAccount(any());
    }

    @Test
    void farmerTokenReturnsOwnProfile() throws Exception {
        UUID id = UUID.randomUUID();
        given(profileService.getForAccount(id)).willReturn(new FarmerProfileResponse(id, "Ravi Kumar",
                "+919876543210", null, "te", null, null, null, null, "FARMER", Instant.now()));

        mockMvc.perform(get("/api/farmers/me")
                        .with(jwt().jwt(j -> j.subject(id.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_FARMER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.fullName").value("Ravi Kumar"));
        verify(profileService).getForAccount(id);
    }

    @Test
    void adminTokenIsForbidden() throws Exception {
        mockMvc.perform(get("/api/farmers/me")
                        .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        verify(profileService, never()).getForAccount(any());
    }

    @Test
    void inactiveAccountReturns401() throws Exception {
        UUID id = UUID.randomUUID();
        given(profileService.getForAccount(id)).willThrow(new AuthenticationRequiredException());

        mockMvc.perform(get("/api/farmers/me")
                        .with(jwt().jwt(j -> j.subject(id.toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_FARMER"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }
}
