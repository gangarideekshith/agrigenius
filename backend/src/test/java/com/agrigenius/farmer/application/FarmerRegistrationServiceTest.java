package com.agrigenius.farmer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.agrigenius.farmer.api.dto.FarmerProfileResponse;
import com.agrigenius.farmer.api.dto.RegisterFarmerRequest;
import com.agrigenius.farmer.domain.FarmerProfile;
import com.agrigenius.farmer.infrastructure.FarmerProfileRepository;
import com.agrigenius.identity.application.UserAccountService;
import com.agrigenius.identity.application.UserAccountService.AccountView;
import com.agrigenius.identity.domain.Role;
import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FarmerRegistrationServiceTest {

    @Mock
    UserAccountService accounts;

    @Mock
    FarmerProfileRepository profiles;

    @Test
    void registersFarmerWithNormalisedMobileAndProfile() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-17T00:00:00Z");
        given(accounts.createAccount("+919876543210", "ravi@example.com", "s3cure-pass", Role.FARMER))
                .willReturn(new AccountView(id, "+919876543210", "ravi@example.com", Role.FARMER, true, now));
        given(profiles.saveAndFlush(any(FarmerProfile.class))).willAnswer(inv -> inv.getArgument(0));
        FarmerRegistrationService service =
                new FarmerRegistrationService(accounts, profiles, Clock.fixed(now, ZoneOffset.UTC));

        FarmerProfileResponse response = service.register(new RegisterFarmerRequest(
                "Ravi Kumar", "98765 43210", "s3cure-pass", "te", "ravi@example.com",
                "Telangana", "Warangal", "Hanamkonda"));

        ArgumentCaptor<FarmerProfile> saved = ArgumentCaptor.forClass(FarmerProfile.class);
        verify(profiles).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(id);
        assertThat(saved.getValue().getPreferredLanguage()).isEqualTo("te");
        assertThat(saved.getValue().getFullName()).isEqualTo("Ravi Kumar");
        assertThat(response.id()).isEqualTo(id);
        assertThat(response.mobileNumber()).isEqualTo("+919876543210");
        assertThat(response.role()).isEqualTo("FARMER");
        assertThat(response.village()).isEqualTo("Hanamkonda");
    }

    @Test
    void responseHasNoCredentialFields() {
        assertThat(Arrays.stream(FarmerProfileResponse.class.getRecordComponents()).map(RecordComponent::getName))
                .noneMatch(name -> name.toLowerCase().contains("password"));
    }
}
