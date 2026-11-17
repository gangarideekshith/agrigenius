package com.agrigenius.farmer.api.dto;

import java.time.Instant;
import java.util.UUID;

/** The signed-in farmer's own profile. Never contains credentials. */
public record FarmerProfileResponse(
        UUID id,
        String fullName,
        String mobileNumber,
        String email,
        String preferredLanguage,
        String state,
        String district,
        String village,
        String addressLine,
        String role,
        Instant createdAt) {
}
