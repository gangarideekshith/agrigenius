package com.agrigenius.farmer.api.dto;

import com.agrigenius.common.validation.IndianMobileNumber;
import com.agrigenius.common.validation.MobileAndPassword;
import com.agrigenius.common.validation.PasswordDiffersFromMobile;
import com.agrigenius.common.validation.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Locale;

/**
 * Farmer self-registration. There is deliberately no role, id or status field: the server
 * always creates an ACTIVE FARMER account, and unknown JSON properties are ignored.
 * Text fields are trimmed (blank optional fields become {@code null}) before validation.
 */
@PasswordDiffersFromMobile
public record RegisterFarmerRequest(
        @NotBlank
        @Size(min = 2, max = 100)
        @Pattern(regexp = "[\\p{L}\\p{M}][\\p{L}\\p{M} .'-]*",
                message = "may contain only letters, spaces and . ' -")
        String fullName,

        @NotBlank @IndianMobileNumber
        String mobileNumber,

        @NotBlank @ValidPassword
        String password,

        @NotBlank
        @Pattern(regexp = "en|hi|te|kn|ta", message = "must be one of en, hi, te, kn, ta")
        String preferredLanguage,

        @Email @Size(max = 254)
        String email,

        @Size(max = 60) String state,
        @Size(max = 60) String district,
        @Size(max = 100) String village) implements MobileAndPassword {

    public RegisterFarmerRequest {
        fullName = trimToNull(fullName);
        mobileNumber = trimToNull(mobileNumber);
        preferredLanguage = preferredLanguage == null ? null : preferredLanguage.trim().toLowerCase(Locale.ROOT);
        email = trimToNull(email);
        email = email == null ? null : email.toLowerCase(Locale.ROOT);
        state = trimToNull(state);
        district = trimToNull(district);
        village = trimToNull(village);
        // password is intentionally not trimmed
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @Override
    public String toString() {
        return "RegisterFarmerRequest[password=<redacted>]";
    }
}
