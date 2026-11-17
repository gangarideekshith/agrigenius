package com.agrigenius.identity.application;

import com.agrigenius.common.error.TooManyRequestsException;
import com.agrigenius.identity.config.ThrottlingProperties;
import java.time.Clock;
import org.springframework.stereotype.Component;

/**
 * Brute-force protection for login and registration (ADR-0003, decision D5).
 *
 * <ul>
 *   <li>Per client IP: at most {@code requestsPerIp} auth requests per window.</li>
 *   <li>Per mobile number: at most {@code failedLoginsPerMobile} failed logins per window;
 *       a successful login clears the count.</li>
 * </ul>
 *
 * <p>No account lockout: limits expire with the window, so attackers cannot lock farmers out
 * permanently. Counters are in memory and therefore per instance.
 */
@Component
public class AuthThrottle {

    static final int MAX_KEYS = 100_000;

    private final FixedWindowCounter requestsPerIp;
    private final FixedWindowCounter failedLoginsPerMobile;

    public AuthThrottle(ThrottlingProperties properties, Clock clock) {
        this.requestsPerIp = new FixedWindowCounter(properties.requestsPerIp(), properties.window(), clock, MAX_KEYS);
        this.failedLoginsPerMobile =
                new FixedWindowCounter(properties.failedLoginsPerMobile(), properties.window(), clock, MAX_KEYS);
    }

    /** Counts one auth request from this IP; throws if the IP is over its limit. */
    public void checkIpAllowed(String ip) {
        if (!requestsPerIp.tryIncrement(ip)) {
            throw new TooManyRequestsException(requestsPerIp.retryAfter(ip));
        }
    }

    /** Throws if this number has too many recent failed logins. Does not count an attempt. */
    public void checkLoginAllowed(String normalizedMobile) {
        if (failedLoginsPerMobile.isLimited(normalizedMobile)) {
            throw new TooManyRequestsException(failedLoginsPerMobile.retryAfter(normalizedMobile));
        }
    }

    public void recordLoginFailure(String normalizedMobile) {
        failedLoginsPerMobile.increment(normalizedMobile);
    }

    public void recordLoginSuccess(String normalizedMobile) {
        failedLoginsPerMobile.reset(normalizedMobile);
    }
}
