package com.example.kitchensink.model;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Covers gaps #1-#5 from reverse_en/test-behavior.md §3 (Member field validation).
class MemberValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    private Member validMember() {
        Member m = new Member();
        m.setName("Jane Doe");
        m.setEmail("jane@example.com");
        m.setPhoneNumber("2125551234");
        return m;
    }

    private Set<ConstraintViolation<Member>> violationsOf(Member m) {
        return validator.validate(m);
    }

    // Gap #1: name containing digits must fail @Pattern(regexp="[^0-9]*")
    @Test
    void name_withDigits_failsPattern() {
        Member m = validMember();
        m.setName("Jane123");
        assertThat(violationsOf(m))
                .extracting(cv -> cv.getPropertyPath().toString())
                .contains("name");
    }

    // Gap #2a: empty name must fail @Size(min=1)
    @Test
    void name_empty_failsSize() {
        Member m = validMember();
        m.setName("");
        assertThat(violationsOf(m)).isNotEmpty();
    }

    // Gap #2b: 25-char name must pass
    @Test
    void name_25chars_passes() {
        Member m = validMember();
        m.setName("A".repeat(25));
        assertThat(violationsOf(m)).isEmpty();
    }

    // Gap #2c: 26-char name must fail @Size(max=25)
    @Test
    void name_26chars_failsSize() {
        Member m = validMember();
        m.setName("A".repeat(26));
        assertThat(violationsOf(m))
                .extracting(cv -> cv.getPropertyPath().toString())
                .contains("name");
    }

    // Gap #3: invalid email format must fail @Email
    @Test
    void email_invalidFormat_failsEmail() {
        Member m = validMember();
        m.setEmail("not-an-email");
        assertThat(violationsOf(m))
                .extracting(cv -> cv.getPropertyPath().toString())
                .contains("email");
    }

    // Gap #4a: 9-char phone must fail @Size(min=10)
    @Test
    void phone_9chars_failsSize() {
        Member m = validMember();
        m.setPhoneNumber("212555123");
        assertThat(violationsOf(m))
                .extracting(cv -> cv.getPropertyPath().toString())
                .contains("phoneNumber");
    }

    // Gap #4b: 10-char phone must pass
    @Test
    void phone_10chars_passes() {
        Member m = validMember();
        m.setPhoneNumber("2125551234");
        assertThat(violationsOf(m)).isEmpty();
    }

    // Gap #4c: 12-char phone must pass
    @Test
    void phone_12chars_passes() {
        Member m = validMember();
        m.setPhoneNumber("212555123456");
        assertThat(violationsOf(m)).isEmpty();
    }

    // Gap #4d: 13-char phone must fail @Size(max=12)
    @Test
    void phone_13chars_failsSize() {
        Member m = validMember();
        m.setPhoneNumber("2125551234567");
        assertThat(violationsOf(m))
                .extracting(cv -> cv.getPropertyPath().toString())
                .contains("phoneNumber");
    }

    // Gap #5: phone with non-digit characters must fail @Digits
    @Test
    void phone_nonDigits_failsDigits() {
        Member m = validMember();
        m.setPhoneNumber("212-555-1234");
        assertThat(violationsOf(m))
                .extracting(cv -> cv.getPropertyPath().toString())
                .contains("phoneNumber");
    }
}
