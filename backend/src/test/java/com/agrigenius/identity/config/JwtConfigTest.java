package com.agrigenius.identity.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.function.Consumer;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtConfigTest {

    private static final String SECRET =
            Base64.getEncoder().encodeToString("agrigenius-test-only-signing-key-NOT-A-SECRET-00".getBytes(StandardCharsets.UTF_8));
    private static final String OTHER_SECRET =
            Base64.getEncoder().encodeToString("a-completely-different-test-key-of-48-bytes-0000".getBytes(StandardCharsets.UTF_8));

    private final JwtProperties properties = new JwtProperties("agrigenius-test", "agrigenius-api", SECRET, Duration.ofMinutes(30));
    private final JwtConfig config = new JwtConfig();
    private final Clock clock = Clock.systemUTC();
    private final JwtEncoder encoder = config.jwtEncoder(properties);
    private final JwtDecoder decoder = config.jwtDecoder(properties, clock);

    private String token(Consumer<JwtClaimsSet.Builder> customizer) {
        return token(encoder, customizer);
    }

    private static String token(JwtEncoder jwtEncoder, Consumer<JwtClaimsSet.Builder> customizer) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer("agrigenius-test")
                .audience(List.of("agrigenius-api"))
                .subject("3f2c6a2e-6b1d-4c8e-9f0a-1d2e3f4a5b6c")
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plus(Duration.ofMinutes(30)))
                .claim(JwtConfig.ROLES_CLAIM, List.of("FARMER"));
        customizer.accept(claims);
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }

    @Test
    void validTokenIsAcceptedAndRolesMapped() {
        Jwt jwt = decoder.decode(token(c -> c.claim(JwtConfig.ROLES_CLAIM, List.of("FARMER", "SUPERUSER"))));

        assertThat(jwt.getSubject()).isEqualTo("3f2c6a2e-6b1d-4c8e-9f0a-1d2e3f4a5b6c");
        assertThat(JwtConfig.rolesConverter().convert(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_FARMER");
    }

    @Test
    void expiredTokenIsRejected() {
        Instant past = Instant.now().minus(Duration.ofHours(2));
        String expired = token(c -> c.issuedAt(past).notBefore(past).expiresAt(Instant.now().minusSeconds(120)));
        assertThatThrownBy(() -> decoder.decode(expired)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenWithinClockSkewIsAccepted() {
        Instant past = Instant.now().minus(Duration.ofHours(1));
        String justExpired = token(c -> c.issuedAt(past).notBefore(past).expiresAt(Instant.now().minusSeconds(10)));
        assertThat(decoder.decode(justExpired).getSubject()).isNotBlank();
    }

    @Test
    void notYetValidTokenIsRejected() {
        String future = token(c -> c.notBefore(Instant.now().plus(Duration.ofMinutes(5))));
        assertThatThrownBy(() -> decoder.decode(future)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() {
        JwtProperties other = new JwtProperties("agrigenius-test", "agrigenius-api", OTHER_SECRET, Duration.ofMinutes(30));
        String forged = token(config.jwtEncoder(other), c -> { });
        assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
    }

    @Test
    void wrongIssuerIsRejected() {
        assertThatThrownBy(() -> decoder.decode(token(c -> c.issuer("evil")))).isInstanceOf(JwtException.class);
    }

    @Test
    void wrongAudienceIsRejected() {
        assertThatThrownBy(() -> decoder.decode(token(c -> c.audience(List.of("other-api")))))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void missingExpiryIsRejected() {
        String noExp = token(c -> c.claims(claims -> claims.remove("exp")));
        assertThatThrownBy(() -> decoder.decode(noExp)).isInstanceOf(JwtException.class);
    }

    @Test
    void missingSubjectIsRejected() {
        String noSub = token(c -> c.claims(claims -> claims.remove("sub")));
        assertThatThrownBy(() -> decoder.decode(noSub)).isInstanceOf(JwtException.class);
    }

    @Test
    void missingRolesIsRejected() {
        String noRoles = token(c -> c.claims(claims -> claims.remove(JwtConfig.ROLES_CLAIM)));
        assertThatThrownBy(() -> decoder.decode(noRoles)).isInstanceOf(JwtException.class);
    }

    @Test
    void unsignedAlgNoneTokenIsRejected() {
        String unsigned = b64("{\"alg\":\"none\",\"typ\":\"JWT\"}") + "." + b64(payload()) + ".";
        assertThatThrownBy(() -> decoder.decode(unsigned)).isInstanceOf(JwtException.class);
    }

    @Test
    void hs512TokenWithSameSecretIsRejected() throws Exception {
        String signingInput = b64("{\"alg\":\"HS512\",\"typ\":\"JWT\"}") + "." + b64(payload());
        Mac mac = Mac.getInstance("HmacSHA512");
        mac.init(new SecretKeySpec(properties.secretBytes(), "HmacSHA512"));
        String signature = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
        assertThatThrownBy(() -> decoder.decode(signingInput + "." + signature)).isInstanceOf(JwtException.class);
    }

    private static String payload() {
        long now = Instant.now().getEpochSecond();
        return "{\"iss\":\"agrigenius-test\",\"aud\":[\"agrigenius-api\"],\"sub\":\"3f2c6a2e-6b1d-4c8e-9f0a-1d2e3f4a5b6c\","
                + "\"iat\":" + now + ",\"nbf\":" + now + ",\"exp\":" + (now + 600) + ",\"roles\":[\"FARMER\"]}";
    }

    private static String b64(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
