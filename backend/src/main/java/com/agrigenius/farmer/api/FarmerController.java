package com.agrigenius.farmer.api;

import com.agrigenius.config.OpenApiConfig;
import com.agrigenius.farmer.api.dto.FarmerProfileResponse;
import com.agrigenius.farmer.application.FarmerProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import com.agrigenius.farmer.api.dto.UpdateFarmerProfileRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in farmer's own data. The account id always comes from the verified token,
 * never from the request, so a farmer can only ever see their own profile.
 * Access to {@code /api/farmers/**} requires ROLE_FARMER (see SecurityConfig).
 */
@RestController
@RequestMapping("/api/farmers")
@Tag(name = "Farmers")
@SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
public class FarmerController {

    private final FarmerProfileService profileService;

    public FarmerController(FarmerProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get the signed-in farmer's profile")
    public FarmerProfileResponse me(@AuthenticationPrincipal Jwt jwt) {
        return profileService.getForAccount(UUID.fromString(jwt.getSubject()));
    }

    @PutMapping("/me")
    @Operation(summary = "Update the signed-in farmer's profile",
            description = "Replaces the editable fields (name, language, email, state, district, village, address). "
                    + "The mobile number cannot be changed.")
    public FarmerProfileResponse updateMe(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateFarmerProfileRequest request) {
        return profileService.updateForAccount(UUID.fromString(jwt.getSubject()), request);
    }
}
