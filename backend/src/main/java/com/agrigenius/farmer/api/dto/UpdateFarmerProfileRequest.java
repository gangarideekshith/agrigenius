package com.agrigenius.farmer.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/**
 * Full replacement of the editable profile fields (PUT semantics: omitted optional fields are cleared).
 * The mobile number, role and status are not editable; unknown JSON properties are ignored.
 * Same rules as registration.
 */
public record UpdateFarmerProfileRequest(
        @NotBlank
        @Size(min = 2, max = 100)
        @Pattern(regexp = "[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]*", message = "may contain only letters, spaces and . ' -")
        String fullName,

        @NotBlank
        @Pattern(regexp = "en|hi|te|kn|ta", message = "must be one of en, hi, te, kn, ta")
        String preferredLanguage,

        @Email @Size(max = 254)
        String email,

        @Size(max = 60) String state,
        @Size(max = 60) String district,
        @Size(max = 100) String village,
        @Size(max = 200) String addressLine) {

    public UpdateFarmerProfileRequest {
        fullName = trimToNull(fullName);
        preferredLanguage = preferredLanguage == null ? null : preferredLanguage.trim().toLowerCase(Locale.ROOT);
        email = trimToNull(email);
        email = email == null ? null : email.toLowerCase(Locale.ROOT);
        state = trimToNull(state);
        district = trimToNull(district);
        village = trimToNull(village);
        addressLine = trimToNull(addressLine);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
