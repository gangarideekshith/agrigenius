package com.agrigenius.identity.api;

import com.agrigenius.common.error.InvalidCredentialsException;
import com.agrigenius.common.validation.MobileNumbers;
import com.agrigenius.identity.api.dto.LoginRequest;
import com.agrigenius.identity.api.dto.LoginResponse;
import com.agrigenius.identity.application.AccessTokenService;
import com.agrigenius.identity.application.AccessTokenService.IssuedToken;
import com.agrigenius.identity.application.AuthThrottle;
import com.agrigenius.identity.application.UserAccountService;
import com.agrigenius.identity.application.UserAccountService.AccountView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login with mobile number and password. Unknown number, wrong password and disabled account
 * all produce the same 401; repeated failures are throttled (429). Passwords and tokens are never logged.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final UserAccountService accounts;
    private final AccessTokenService tokens;
    private final AuthThrottle throttle;

    public AuthController(AuthenticationManager authenticationManager, UserAccountService accounts,
            AccessTokenService tokens, AuthThrottle throttle) {
        this.authenticationManager = authenticationManager;
        this.accounts = accounts;
        this.tokens = tokens;
        this.throttle = throttle;
    }

    @PostMapping("/login")
    @Operation(summary = "Log in with mobile number and password", description = "Returns a short-lived bearer token.")
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        throttle.checkIpAllowed(httpRequest.getRemoteAddr());
        String mobile = MobileNumbers.normalize(request.mobileNumber()).orElseThrow(InvalidCredentialsException::new);
        throttle.checkLoginAllowed(mobile);

        UUID accountId;
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(mobile, request.password()));
            accountId = UUID.fromString(authentication.getName());
        } catch (AuthenticationException failed) {
            throttle.recordLoginFailure(mobile);
            log.info("Login failed mobile={}", MobileNumbers.mask(mobile));
            throw new InvalidCredentialsException();
        }

        // Checked after the password so disabled accounts look exactly like wrong passwords.
        AccountView account = accounts.findActiveById(accountId).orElse(null);
        if (account == null) {
            throttle.recordLoginFailure(mobile);
            log.info("Login rejected for inactive account accountId={}", accountId);
            throw new InvalidCredentialsException();
        }

        throttle.recordLoginSuccess(mobile);
        accounts.recordSuccessfulLogin(account.id());
        IssuedToken token = tokens.issue(account.id(), account.role());
        log.info("Login succeeded accountId={}", account.id());
        return new LoginResponse(token.value(), "Bearer", token.expiresIn().toSeconds(),
                new LoginResponse.UserSummary(account.id(), account.role().name()));
    }
}
