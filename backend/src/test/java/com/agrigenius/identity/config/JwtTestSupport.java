package com.agrigenius.identity.config;

import java.time.Clock;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;

/** Exposes JwtConfig's package-private factory methods to tests in other packages. */
public final class JwtTestSupport {

    private JwtTestSupport() {
    }

    public static JwtEncoder encoder(JwtProperties properties) {
        return new JwtConfig().jwtEncoder(properties);
    }

    public static JwtDecoder decoder(JwtProperties properties, Clock clock) {
        return new JwtConfig().jwtDecoder(properties, clock);
    }
}
