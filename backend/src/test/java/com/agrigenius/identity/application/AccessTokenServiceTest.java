package com.agrigenius.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.agrigenius.identity.application.AccessTokenService.IssuedToken;
import com.agrigenius.identity.config.JwtProperties;
import com.agrigenius.identity.config.JwtTestSupport;
import com.agrigenius.identity.domain.Role;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class AccessTokenServiceTest {

    private static final String SECRET =
            Base64.getEncoder().encodeToString("agrigenius-test-only-signing-key-NOT-A-SECRET-00".getBytes(StandardCharsets.UTF_8));

    private final JwtProperties properties = new JwtProperties("agrigenius-test", "agrigenius-api", SECRET, Duration.ofMinutes(30));
    private final Clock clock = Clock.systemUTC();
    private final AccessTokenService service =
            new AccessTokenService(JwtTestSupport.encoder(properties), properties, clock);
    private final JwtDecoder decoder = JwtTestSupport.decoder(properties, clock);

    @Test
    void issuesTokenWithExpectedClaimsAndNoPersonalData() {
        UUID accountId = UUID.randomUUID();

        IssuedToken issued = service.issue(accountId, Role.FARMER);
        Jwt jwt = decoder.decode(issued.value());

        assertThat(jwt.getClaimAsString("iss")).isEqualTo("agrigenius-test"); // not a URL, so not getIssuer()
        assertThat(jwt.getAudience()).containsExactly("agrigenius-api");
        assertThat(jwt.getSubject()).isEqualTo(accountId.toString());
        assertThat(jwt.getClaimAsStringList("roles")).isEqualTo(List.of("FARMER"));
        assertThat(jwt.getIssuedAt()).isNotNull();
        assertThat(jwt.getNotBefore()).isEqualTo(jwt.getIssuedAt());
        assertThat(jwt.getExpiresAt()).isEqualTo(jwt.getIssuedAt().plus(Duration.ofMinutes(30)));
        assertThat(jwt.getId()).isNotBlank();
        assertThat(jwt.getClaims().keySet())
                .containsExactlyInAnyOrder("iss", "aud", "sub", "roles", "iat", "nbf", "exp", "jti");
    }

    @Test
    void expiresInEqualsConfiguredTtl() {
        assertThat(service.issue(UUID.randomUUID(), Role.ADMIN).expiresIn()).isEqualTo(Duration.ofMinutes(30));
    }

    @Test
    void eachTokenHasUniqueId() {
        UUID id = UUID.randomUUID();
        String a = decoder.decode(service.issue(id, Role.FARMER).value()).getId();
        String b = decoder.decode(service.issue(id, Role.FARMER).value()).getId();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void toStringRedactsTokenValue() {
        IssuedToken issued = service.issue(UUID.randomUUID(), Role.FARMER);
        assertThat(issued.toString()).doesNotContain(issued.value()).contains("<redacted>");
    }
}
