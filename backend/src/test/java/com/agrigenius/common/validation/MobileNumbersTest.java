package com.agrigenius.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MobileNumbersTest {

    @ParameterizedTest
    @CsvSource({
        "9876543210, +919876543210",
        "+919876543210, +919876543210",
        "98765 43210, +919876543210",
        "+91 98765-43210, +919876543210",
        "6000000000, +916000000000",
        "7-8-9-0-1-2-3-4-5-6, +917890123456"
    })
    void normalizesValidNumbers(String raw, String expected) {
        assertThat(MobileNumbers.normalize(raw)).contains(expected);
        assertThat(MobileNumbers.isValid(raw)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "5876543210",      // must start 6-9
        "987654321",       // 9 digits
        "98765432101",     // 11 digits
        "98765abcde",      // letters
        "09876543210",     // leading 0
        "919876543210",    // 91 without +
        "+9198765432",     // too short after +91
        "+449876543210",   // other country code
        "   "
    })
    void rejectsInvalidNumbers(String raw) {
        assertThat(MobileNumbers.normalize(raw)).isEmpty();
        assertThat(MobileNumbers.isValid(raw)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void nullAndEmptyAreInvalid(String raw) {
        assertThat(MobileNumbers.normalize(raw)).isEmpty();
    }

    @Test
    void masksAllButLastFourDigits() {
        assertThat(MobileNumbers.mask("+919876543210")).isEqualTo("******3210");
        assertThat(MobileNumbers.mask("98765 43210")).isEqualTo("******3210");
    }

    @Test
    void maskHandlesNullAndShortInput() {
        assertThat(MobileNumbers.mask(null)).isEqualTo("******");
        assertThat(MobileNumbers.mask("12")).isEqualTo("******");
        assertThat(MobileNumbers.mask("")).isEqualTo("******");
    }
}
