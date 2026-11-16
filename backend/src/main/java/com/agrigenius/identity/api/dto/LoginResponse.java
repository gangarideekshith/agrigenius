package com.agrigenius.identity.api.dto;

import java.util.UUID;

/** Bearer access token for {@code Authorization: Bearer <accessToken>}; {@code expiresIn} is in seconds. */
public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserSummary user) {

    public record UserSummary(UUID id, String role) {
    }

    @Override
    public String toString() {
        return "LoginResponse[accessToken=<redacted>, expiresIn=" + expiresIn + ", user=" + user + "]";
    }
}
