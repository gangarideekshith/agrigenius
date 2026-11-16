package com.agrigenius.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValidationConstraintsTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    record MobileHolder(@IndianMobileNumber String mobileNumber) {
    }

    record PasswordHolder(@ValidPassword String password) {
    }

    @PasswordDiffersFromMobile
    record Credentials(String mobileNumber, String password) implements MobileAndPassword {
    }

    @Nested
    class IndianMobileNumberConstraint {

        @Test
        void acceptsValidAndNull() {
            assertThat(validator.validate(new MobileHolder("+91 98765 43210"))).isEmpty();
            assertThat(validator.validate(new MobileHolder(null))).isEmpty();
        }

        @Test
        void rejectsInvalidWithMessage() {
            Set<ConstraintViolation<MobileHolder>> violations = validator.validate(new MobileHolder("12345"));
            assertThat(violations).singleElement().satisfies(v -> {
                assertThat(v.getPropertyPath().toString()).isEqualTo("mobileNumber");
                assertThat(v.getMessage()).isEqualTo("must be a valid 10-digit Indian mobile number");
            });
        }
    }

    @Nested
    class ValidPasswordConstraint {

        @ParameterizedTest
        @ValueSource(strings = {
            "abcdefg1",                                                           // exactly 8
            "s3cure-pass",
            "Passw0rd with spaces",
            "పాస్వర్డ్1234"                                                          // Telugu letters + digits, under 72 bytes
        })
        void acceptsValidPasswords(String password) {
            assertThat(validator.validate(new PasswordHolder(password))).isEmpty();
        }

        @Test
        void acceptsExactly64AsciiCharacters() {
            String password = "a".repeat(63) + "1";
            assertThat(password).hasSize(64);
            assertThat(validator.validate(new PasswordHolder(password))).isEmpty();
        }

        @Test
        void nullIsValid() {
            assertThat(validator.validate(new PasswordHolder(null))).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "abc1234",          // 7 characters
            "abcdefgh",         // letters only
            "12345678",         // digits only
            "!!!!????"          // neither
        })
        void rejectsPolicyViolations(String password) {
            assertThat(validator.validate(new PasswordHolder(password)))
                    .singleElement()
                    .extracting(ConstraintViolation::getMessage)
                    .isEqualTo("must be 8-64 characters with at least one letter and one digit");
        }

        @Test
        void rejects65Characters() {
            String password = "a".repeat(64) + "1";
            assertThat(validator.validate(new PasswordHolder(password)))
                    .singleElement()
                    .extracting(ConstraintViolation::getMessage)
                    .isEqualTo("must be 8-64 characters with at least one letter and one digit");
        }

        @Test
        void rejectsPasswordWithinCharacterLimitButOver72Bytes() {
            // Hindi letters are 3 bytes each in UTF-8: 30 letters + 1 digit = 31 characters, 91 bytes.
            String password = "क".repeat(30) + "1";
            assertThat(password.codePointCount(0, password.length())).isLessThanOrEqualTo(64);
            assertThat(password.getBytes(StandardCharsets.UTF_8).length).isGreaterThan(72);

            assertThat(validator.validate(new PasswordHolder(password)))
                    .singleElement()
                    .extracting(ConstraintViolation::getMessage)
                    .isEqualTo("is too long (maximum 72 bytes when encoded as UTF-8)");
        }

        @Test
        void accepts72BytesExactly() {
            // 23 Telugu letters (3 bytes each) + 3 digits = 72 bytes.
            String password = "క".repeat(23) + "123";
            assertThat(password.getBytes(StandardCharsets.UTF_8).length).isEqualTo(72);
            assertThat(validator.validate(new PasswordHolder(password))).isEmpty();
        }
    }

    @Nested
    class PasswordDiffersFromMobileConstraint {

        @Test
        void acceptsDifferentPassword() {
            assertThat(validator.validate(new Credentials("9876543210", "s3cure-pass"))).isEmpty();
        }

        @ParameterizedTest
        @ValueSource(strings = {"9876543210", "+919876543210"})
        void rejectsPasswordEqualToMobileInAnyForm(String password) {
            assertViolationOnPassword(new Credentials("+91 98765-43210", password));
        }

        @Test
        void rejectsPasswordEqualToRawMobile() {
            assertViolationOnPassword(new Credentials("98765 43210", "98765 43210"));
        }

        @Test
        void rejectsPasswordEqualToRawEvenWhenMobileIsInvalid() {
            assertViolationOnPassword(new Credentials("12345abc", "12345abc"));
        }

        @Test
        void nullsAreValid() {
            assertThat(validator.validate(new Credentials(null, "9876543210"))).isEmpty();
            assertThat(validator.validate(new Credentials("9876543210", null))).isEmpty();
        }

        private void assertViolationOnPassword(Credentials credentials) {
            assertThat(validator.validate(credentials)).singleElement().satisfies(v -> {
                assertThat(v.getPropertyPath().toString()).isEqualTo("password");
                assertThat(v.getMessage()).isEqualTo("must not be the same as the mobile number");
            });
        }
    }
}
