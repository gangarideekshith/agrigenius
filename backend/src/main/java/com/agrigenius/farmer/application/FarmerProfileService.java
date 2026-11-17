package com.agrigenius.farmer.application;

import com.agrigenius.common.error.AuthenticationRequiredException;
import com.agrigenius.farmer.api.dto.FarmerProfileResponse;
import com.agrigenius.farmer.api.dto.UpdateFarmerProfileRequest;
import com.agrigenius.farmer.domain.FarmerProfile;
import com.agrigenius.farmer.domain.PreferredLanguage;
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

    /**
     * Owner-only update of the editable fields; account id comes from the verified token.
     * Profile and email change commit together or not at all.
     */
    @Transactional
    public FarmerProfileResponse updateForAccount(UUID accountId, UpdateFarmerProfileRequest request) {
        accounts.findActiveById(accountId).orElseThrow(AuthenticationRequiredException::new);
        FarmerProfile profile = profiles.findById(accountId).orElseThrow(AuthenticationRequiredException::new);
        PreferredLanguage language = PreferredLanguage.fromCode(request.preferredLanguage())
                .orElseThrow(() -> new IllegalArgumentException("preferredLanguage must be validated before update"));
        AccountView account = accounts.changeEmail(accountId, request.email());
        profile.update(request.fullName(), language, request.state(), request.district(), request.village(),
                request.addressLine());
        return toResponse(account, profiles.saveAndFlush(profile));
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
