package com.agrigenius.farmer.domain;

import java.util.Arrays;
import java.util.Optional;

/** Languages the platform plans to support (PROJECT_SPEC 3.3). Stored and exchanged as ISO 639-1 codes. */
public enum PreferredLanguage {
    ENGLISH("en"),
    HINDI("hi"),
    TELUGU("te"),
    KANNADA("kn"),
    TAMIL("ta");

    private final String code;

    PreferredLanguage(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Optional<PreferredLanguage> fromCode(String code) {
        return Arrays.stream(values()).filter(l -> l.code.equals(code)).findFirst();
    }
}
