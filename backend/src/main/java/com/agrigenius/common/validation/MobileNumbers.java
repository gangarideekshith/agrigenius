package com.agrigenius.common.validation;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Indian mobile number parsing, normalisation ({@code +91XXXXXXXXXX}) and log-safe masking. */
public final class MobileNumbers {

    private static final Pattern ACCEPTED = Pattern.compile("^(\\+91)?([6-9]\\d{9})$");

    private MobileNumbers() {
    }

    /** Removes spaces and hyphens and returns {@code +91} plus the 10-digit number, or empty if invalid. */
    public static Optional<String> normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String compact = raw.replace(" ", "").replace("-", "");
        Matcher matcher = ACCEPTED.matcher(compact);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of("+91" + matcher.group(2));
    }

    public static boolean isValid(String raw) {
        return normalize(raw).isPresent();
    }

    /** {@code ******} plus the last four digits, safe for logs. */
    public static String mask(String normalizedOrRaw) {
        if (normalizedOrRaw == null) {
            return "******";
        }
        String digits = normalizedOrRaw.replaceAll("\\D", "");
        if (digits.length() < 4) {
            return "******";
        }
        return "******" + digits.substring(digits.length() - 4);
    }
}
