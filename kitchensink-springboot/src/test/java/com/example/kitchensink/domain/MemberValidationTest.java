package com.example.kitchensink.domain;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class MemberValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setup() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private Member validMember() {
        Member m = new Member();
        m.setName("Jane Doe");
        m.setEmail("jane@mailinator.com");
        m.setPhoneNumber("2125551234");
        return m;
    }

    // Gaps 1-4: name constraints
    @Test
    void validNamePasses() {
        assertThat(validator.validateProperty(validMember(), "name")).isEmpty();
    }

    @Test
    void nameWithDigitsIsRejected() {
        Member m = validMember();
        m.setName("Jane123");
        assertThat(validator.validateProperty(m, "name")).isNotEmpty();
    }

    @Test
    void emptyNameIsRejected() {
        Member m = validMember();
        m.setName("");
        assertThat(validator.validateProperty(m, "name")).isNotEmpty();
    }

    @Test
    void nullNameIsRejected() {
        Member m = validMember();
        m.setName(null);
        assertThat(validator.validateProperty(m, "name")).isNotEmpty();
    }

    @Test
    void nameTooLongIsRejected() {
        Member m = validMember();
        m.setName("A".repeat(26));
        assertThat(validator.validateProperty(m, "name")).isNotEmpty();
    }

    // Gaps 5-7: email constraints
    @Test
    void validEmailPasses() {
        assertThat(validator.validateProperty(validMember(), "email")).isEmpty();
    }

    @Test
    void malformedEmailIsRejected() {
        Member m = validMember();
        m.setEmail("not-an-email");
        assertThat(validator.validateProperty(m, "email")).isNotEmpty();
    }

    @Test
    void nullEmailIsRejected() {
        Member m = validMember();
        m.setEmail(null);
        assertThat(validator.validateProperty(m, "email")).isNotEmpty();
    }

    @Test
    void emptyEmailIsRejected() {
        Member m = validMember();
        m.setEmail("");
        assertThat(validator.validateProperty(m, "email")).isNotEmpty();
    }

    // Gaps 8-10: phone constraints
    @Test
    void phoneTooShortIsRejected() {
        Member m = validMember();
        m.setPhoneNumber("123456789"); // 9 digits
        assertThat(validator.validateProperty(m, "phoneNumber")).isNotEmpty();
    }

    @Test
    void phoneTooLongIsRejected() {
        Member m = validMember();
        m.setPhoneNumber("1234567890123"); // 13 chars
        assertThat(validator.validateProperty(m, "phoneNumber")).isNotEmpty();
    }

    @Test
    void phoneWithNonDigitsIsRejected() {
        Member m = validMember();
        m.setPhoneNumber("212-555-1234"); // contains dashes
        assertThat(validator.validateProperty(m, "phoneNumber")).isNotEmpty();
    }
}
