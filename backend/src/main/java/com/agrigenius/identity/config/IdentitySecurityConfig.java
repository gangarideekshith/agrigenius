package com.agrigenius.identity.config;

import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Password hashing and the login {@link AuthenticationManager}. */
@Configuration
public class IdentitySecurityConfig {

    /** BCrypt cost 12 (~250 ms per hash on a laptop): slow for attackers, acceptable for logins. */
    static final int BCRYPT_STRENGTH = 12;

    /**
     * Stores hashes as {@code {bcrypt}...} so the algorithm or cost can be upgraded later
     * without invalidating existing passwords.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder("bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(BCRYPT_STRENGTH)));
    }

    /**
     * Login authentication. {@link DaoAuthenticationProvider} hashes a dummy password for unknown
     * numbers so response time does not reveal which numbers are registered.
     */
    @Bean
    AuthenticationManager authenticationManager(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }
}
