package com.agrigenius.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agrigenius.common.error.TooManyRequestsException;
import com.agrigenius.identity.config.ThrottlingProperties;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class AuthThrottleTest {

    private static final String MOBILE = "+919876543210";
    private static final String OTHER_MOBILE = "+919123456789";
    private static final String IP = "203.0.113.7";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-17T00:00:00Z"));
    private final AuthThrottle throttle =
            new AuthThrottle(new ThrottlingProperties(5, 20, Duration.ofMinutes(15)), clock);

    @Test
    void ipAllowsUpToLimitThenThrows429() {
        for (int i = 0; i < 20; i++) {
            throttle.checkIpAllowed(IP);
        }

        assertThatThrownBy(() -> throttle.checkIpAllowed(IP))
                .isInstanceOfSatisfying(TooManyRequestsException.class, ex -> {
                    assertThat(ex.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                    assertThat(ex.code()).isEqualTo("TOO_MANY_REQUESTS");
                    assertThat(ex.title()).isEqualTo("Too many requests");
                    assertThat(ex.detail()).isEqualTo("Too many attempts. Please wait and try again.");
                    assertThat(ex.retryAfter()).isEqualTo(Duration.ofMinutes(15));
                });
    }

    @Test
    void ipLimitResetsAfterWindow() {
        for (int i = 0; i < 20; i++) {
            throttle.checkIpAllowed(IP);
        }
        clock.advance(Duration.ofMinutes(15));

        assertThatCode(() -> throttle.checkIpAllowed(IP)).doesNotThrowAnyException();
    }

    @Test
    void ipsAreIndependent() {
        for (int i = 0; i < 20; i++) {
            throttle.checkIpAllowed(IP);
        }

        assertThatCode(() -> throttle.checkIpAllowed("198.51.100.1")).doesNotThrowAnyException();
    }

    @Test
    void loginBlockedAfterFiveFailuresForThatNumber() {
        for (int i = 0; i < 4; i++) {
            throttle.recordLoginFailure(MOBILE);
            assertThatCode(() -> throttle.checkLoginAllowed(MOBILE)).doesNotThrowAnyException();
        }
        throttle.recordLoginFailure(MOBILE);

        assertThatThrownBy(() -> throttle.checkLoginAllowed(MOBILE))
                .isInstanceOf(TooManyRequestsException.class);
        assertThatCode(() -> throttle.checkLoginAllowed(OTHER_MOBILE)).doesNotThrowAnyException();
    }

    @Test
    void checkLoginAllowedDoesNotCountAttempts() {
        for (int i = 0; i < 50; i++) {
            throttle.checkLoginAllowed(MOBILE);
        }

        assertThatCode(() -> throttle.checkLoginAllowed(MOBILE)).doesNotThrowAnyException();
    }

    @Test
    void successfulLoginClearsFailures() {
        for (int i = 0; i < 4; i++) {
            throttle.recordLoginFailure(MOBILE);
        }
        throttle.recordLoginSuccess(MOBILE);
        for (int i = 0; i < 4; i++) {
            throttle.recordLoginFailure(MOBILE);
        }

        assertThatCode(() -> throttle.checkLoginAllowed(MOBILE)).doesNotThrowAnyException();
    }

    @Test
    void loginBlockExpiresWithWindowAndReportsRetryAfter() {
        for (int i = 0; i < 5; i++) {
            throttle.recordLoginFailure(MOBILE);
        }
        clock.advance(Duration.ofMinutes(10));

        assertThatThrownBy(() -> throttle.checkLoginAllowed(MOBILE))
                .isInstanceOfSatisfying(TooManyRequestsException.class,
                        ex -> assertThat(ex.retryAfter()).isEqualTo(Duration.ofMinutes(5)));

        clock.advance(Duration.ofMinutes(5));
        assertThatCode(() -> throttle.checkLoginAllowed(MOBILE)).doesNotThrowAnyException();
    }

    @Test
    void retryAfterIsNeverBelowOneSecond() {
        assertThat(new TooManyRequestsException(Duration.ZERO).retryAfter()).isEqualTo(Duration.ofSeconds(1));
        assertThat(new TooManyRequestsException(Duration.ofSeconds(-5)).retryAfter()).isEqualTo(Duration.ofSeconds(1));
        assertThat(new TooManyRequestsException(null).retryAfter()).isEqualTo(Duration.ofSeconds(1));
    }
}
