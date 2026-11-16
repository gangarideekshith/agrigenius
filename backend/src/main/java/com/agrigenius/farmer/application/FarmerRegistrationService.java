package com.agrigenius.farmer.application;

import com.agrigenius.common.validation.MobileNumbers;
import com.agrigenius.farmer.api.dto.FarmerProfileResponse;
import com.agrigenius.farmer.api.dto.RegisterFarmerRequest;
import com.agrigenius.farmer.domain.FarmerProfile;
import com.agrigenius.farmer.domain.PreferredLanguage;
import com.agrigenius.farmer.infrastructure.FarmerProfileRepository;
import com.agrigenius.identity.application.UserAccountService;
import com.agrigenius.identity.application.UserAccountService.AccountView;
import com.agrigenius.identity.domain.Role;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates a FARMER account and its profile in one transaction: both exist, or neither does. */
@Service
public class FarmerRegistrationService {

    private final UserAccountService accounts;
    private final FarmerProfileRepository profiles;
    private final Clock clock;

    public FarmerRegistrationService(UserAccountService accounts, FarmerProfileRepository profiles, Clock clock) {
        this.accounts = accounts;
        this.profiles = profiles;
        this.clock = clock;
    }

    @Transactional
    public FarmerProfileResponse register(RegisterFarmerRequest request) {
        String mobile = MobileNumbers.normalize(request.mobileNumber())
                .orElseThrow(() -> new IllegalArgumentException("mobileNumber must be validated before registration"));
        PreferredLanguage language = PreferredLanguage.fromCode(request.preferredLanguage())
                .orElseThrow(() -> new IllegalArgumentException("preferredLanguage must be validated before registration"));

        AccountView account = accounts.createAccount(mobile, request.email(), request.password(), Role.FARMER);
        FarmerProfile profile = profiles.saveAndFlush(new FarmerProfile(account.id(), request.fullName(), language,
                request.state(), request.district(), request.village(),
                clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MICROS)));
        return FarmerProfileService.toResponse(account, profile);
    }
}
