package com.agrigenius.farmer.api;

import com.agrigenius.farmer.api.dto.FarmerProfileResponse;
import com.agrigenius.farmer.api.dto.RegisterFarmerRequest;
import com.agrigenius.farmer.application.FarmerRegistrationService;
import com.agrigenius.identity.application.AuthThrottle;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public farmer self-registration. Lives in the farmer module because it creates both the
 * account and the farmer profile (farmer depends on identity, not the reverse).
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class FarmerRegistrationController {

    private static final Logger log = LoggerFactory.getLogger(FarmerRegistrationController.class);

    private final FarmerRegistrationService registrationService;
    private final AuthThrottle throttle;

    public FarmerRegistrationController(FarmerRegistrationService registrationService, AuthThrottle throttle) {
        this.registrationService = registrationService;
        this.throttle = throttle;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a farmer account",
            description = "Creates an ACTIVE FARMER account and profile. Returns the profile; call /api/auth/login for a token.")
    public ResponseEntity<FarmerProfileResponse> register(@Valid @RequestBody RegisterFarmerRequest request,
            HttpServletRequest httpRequest) {
        throttle.checkIpAllowed(httpRequest.getRemoteAddr());
        FarmerProfileResponse profile = registrationService.register(request);
        log.info("Farmer registered accountId={}", profile.id());
        return ResponseEntity.created(URI.create("/api/farmers/me")).body(profile);
    }
}
