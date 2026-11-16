package com.agrigenius.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Shared helpers for the auth integration tests. */
final class AuthTestSupport {

    static final String PASSWORD = "Kh3t-Safe-pass";

    private AuthTestSupport() {
    }

    /** A random valid 10-digit Indian mobile number (starting with 9). */
    static String randomMobile() {
        return "9" + String.format("%09d", ThreadLocalRandom.current().nextLong(1_000_000_000L));
    }

    static String registerJson(String mobile, String password, String email, String extra) {
        return "{\"fullName\":\"Ravi Kumar\",\"mobileNumber\":\"" + mobile + "\",\"password\":\"" + password + "\","
                + "\"preferredLanguage\":\"te\""
                + (email == null ? "" : ",\"email\":\"" + email + "\"")
                + ",\"state\":\"Telangana\",\"district\":\"Warangal\",\"village\":\"Hanamkonda\""
                + (extra == null ? "" : "," + extra)
                + "}";
    }

    static MockHttpServletRequestBuilder register(String json) {
        return post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json);
    }

    static MockHttpServletRequestBuilder login(String mobile, String password) {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mobileNumber\":\"" + mobile + "\",\"password\":\"" + password + "\"}");
    }

    /** Signs an HS256 token with the given key bytes and claims. */
    static String sign(byte[] key, String issuer, String audience, UUID subject, List<String> roles,
            Instant issuedAt, Instant expiresAt) {
        NimbusJwtEncoder encoder = NimbusJwtEncoder.withSecretKey(new SecretKeySpec(key, "HmacSHA256"))
                .algorithm(MacAlgorithm.HS256).build();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer)
                .audience(List.of(audience))
                .subject(subject.toString())
                .issuedAt(issuedAt)
                .notBefore(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("roles", roles)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(), claims))
                .getTokenValue();
    }
}
