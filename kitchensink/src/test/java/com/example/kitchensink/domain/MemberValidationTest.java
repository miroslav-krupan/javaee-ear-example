package com.example.kitchensink.domain;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class MemberValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
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

    private boolean hasViolationOn(Set<ConstraintViolation<Member>> violations, String field) {
        return violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals(field));
    }

    // Gap 1: name with digit fails @Pattern
    @Test
    void nameWithDigitFailsPattern() {
        Member m = validMember();
        m.setName("Jane4Doe");
        assertTrue(hasViolationOn(validator.validate(m), "name"));
    }

    // Gap 2: name empty string fails @Size(min=1)
    @Test
    void nameEmptyStringFailsSize() {
        Member m = validMember();
        m.setName("");
        assertTrue(hasViolationOn(validator.validate(m), "name"));
    }

    // Gap 3: name length 25 passes; length 26 fails @Size(max=25)
    @Test
    void nameLength25Passes() {
        Member m = validMember();
        m.setName("A".repeat(25));
        assertTrue(validator.validate(m).isEmpty());
    }

    @Test
    void nameLength26FailsSize() {
        Member m = validMember();
        m.setName("A".repeat(26));
        assertTrue(hasViolationOn(validator.validate(m), "name"));
    }

    // Gap 4: email invalid format fails @Email
    @Test
    void emailInvalidFormatFailsEmail() {
        Member m = validMember();
        m.setEmail("not-an-email");
        assertTrue(hasViolationOn(validator.validate(m), "email"));
    }

    // Gap 5: email null fails @NotNull; email empty fails @NotBlank
    @Test
    void emailNullFailsNotNull() {
        Member m = validMember();
        m.setEmail(null);
        assertTrue(hasViolationOn(validator.validate(m), "email"));
    }

    @Test
    void emailEmptyFailsNotBlank() {
        Member m = validMember();
        m.setEmail("");
        assertTrue(hasViolationOn(validator.validate(m), "email"));
    }

    // Gap 6: phoneNumber 9 chars fails @Size(min=10)
    @Test
    void phoneNumber9CharsFailsSize() {
        Member m = validMember();
        m.setPhoneNumber("123456789");
        assertTrue(hasViolationOn(validator.validate(m), "phoneNumber"));
    }

    // Gap 7: phoneNumber 13 chars fails @Size(max=12)
    @Test
    void phoneNumber13CharsFailsSize() {
        Member m = validMember();
        m.setPhoneNumber("1234567890123");
        assertTrue(hasViolationOn(validator.validate(m), "phoneNumber"));
    }

    // Gap 8: phoneNumber with letter fails @Digits
    @Test
    void phoneNumberWithLetterFailsDigits() {
        Member m = validMember();
        m.setPhoneNumber("212555123A");
        assertTrue(hasViolationOn(validator.validate(m), "phoneNumber"));
    }
}
