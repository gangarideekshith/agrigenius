package com.agrigenius.identity.application;

import com.agrigenius.identity.config.JwtConfig;
import com.agrigenius.identity.config.JwtProperties;
import com.agrigenius.identity.domain.Role;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues short-lived HS256 access tokens. Tokens identify the account by UUID and carry no personal data. */
@Service
public class AccessTokenService {

    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public AccessTokenService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public record IssuedToken(String value, Duration expiresIn) {
        @Override
        public String toString() {
            return "IssuedToken[value=<redacted>, expiresIn=" + expiresIn + "]";
        }
    }

    public IssuedToken issue(UUID accountId, Role role) {
        Instant now = clock.instant();
        Duration ttl = properties.accessTokenTtl();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(accountId.toString())
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plus(ttl))
                .id(UUID.randomUUID().toString())
                .claim(JwtConfig.ROLES_CLAIM, List.of(role.name()))
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, ttl);
    }
}
