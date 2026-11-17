package com.agrigenius.farmer.application;

import com.agrigenius.common.error.AuthenticationRequiredException;
import com.agrigenius.farmer.api.dto.FarmerProfileResponse;
import com.agrigenius.farmer.domain.FarmerProfile;
import com.agrigenius.farmer.infrastructure.FarmerProfileRepository;
import com.agrigenius.identity.application.UserAccountService;
import com.agrigenius.identity.application.UserAccountService.AccountView;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FarmerProfileService {

    private final FarmerProfileRepository profiles;
    private final UserAccountService accounts;

    public FarmerProfileService(FarmerProfileRepository profiles, UserAccountService accounts) {
        this.profiles = profiles;
        this.accounts = accounts;
    }

    /**
     * The profile of the authenticated account. A token for an account that has since been
     * disabled or deleted, or that has no farmer profile, is treated as unauthenticated.
     */
    @Transactional(readOnly = true)
    public FarmerProfileResponse getForAccount(UUID accountId) {
        AccountView account = accounts.findActiveById(accountId)
                .orElseThrow(AuthenticationRequiredException::new);
        FarmerProfile profile = profiles.findById(accountId)
                .orElseThrow(AuthenticationRequiredException::new);
        return toResponse(account, profile);
    }

    static FarmerProfileResponse toResponse(AccountView account, FarmerProfile profile) {
        return new FarmerProfileResponse(
                account.id(),
                profile.getFullName(),
                account.mobileNumber(),
                account.email(),
                profile.getPreferredLanguage(),
                profile.getState(),
                profile.getDistrict(),
                profile.getVillage(),
                profile.getAddressLine(),
                account.role().name(),
                account.createdAt());
    }
}
