package com.example.kitchensink.model;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

// Gap list items #1-9: Member bean validation constraints
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
        m.setEmail("jane@example.com");
        m.setPhoneNumber("2125551234");
        return m;
    }

    private Set<ConstraintViolation<Member>> violationsFor(Member m) {
        return validator.validate(m);
    }

    // Gap #1 — name blank/null rejected
    @Test
    void name_nullIsRejected() {
        Member m = validMember();
        m.setName(null);
        assertThat(violationsFor(m)).isNotEmpty();
    }

    @Test
    void name_blankIsRejected() {
        Member m = validMember();
        m.setName("");
        assertThat(violationsFor(m)).isNotEmpty();
    }

    // Gap #2 — name pattern: numeric chars rejected
    @Test
    void name_numericCharsRejected() {
        Member m = validMember();
        m.setName("Jane2");
        Set<ConstraintViolation<Member>> violations = violationsFor(m);
        assertThat(violations).anyMatch(v -> v.getPropertyPath().toString().equals("name")
                && v.getMessage().equals("Must not contain numbers"));
    }

    // Gap #3 — name size: max 25 chars enforced
    @Test
    void name_tooLongIsRejected() {
        Member m = validMember();
        m.setName("A".repeat(26));
        assertThat(violationsFor(m)).isNotEmpty();
    }

    @Test
    void name_exactly25CharsIsAccepted() {
        Member m = validMember();
        m.setName("A".repeat(25));
        assertThat(violationsFor(m)).isEmpty();
    }

    // Gap #4 — email null/empty rejected
    @Test
    void email_nullIsRejected() {
        Member m = validMember();
        m.setEmail(null);
        assertThat(violationsFor(m)).isNotEmpty();
    }

    @Test
    void email_emptyIsRejected() {
        Member m = validMember();
        m.setEmail("");
        assertThat(violationsFor(m)).isNotEmpty();
    }

    // Gap #5 — email format validation
    @Test
    void email_invalidFormatIsRejected() {
        Member m = validMember();
        m.setEmail("not-an-email");
        assertThat(violationsFor(m)).isNotEmpty();
    }

    @Test
    void email_validFormatAccepted() {
        Member m = validMember();
        m.setEmail("user@domain.com");
        assertThat(violationsFor(m)).isEmpty();
    }

    // Gap #6 — phoneNumber null rejected
    @Test
    void phoneNumber_nullIsRejected() {
        Member m = validMember();
        m.setPhoneNumber(null);
        assertThat(violationsFor(m)).isNotEmpty();
    }

    // Gap #7 — phoneNumber non-digits rejected
    @Test
    void phoneNumber_nonDigitsRejected() {
        Member m = validMember();
        m.setPhoneNumber("12345abcde");
        assertThat(violationsFor(m)).isNotEmpty();
    }

    // Gap #8 — phoneNumber too short (<10) rejected
    @Test
    void phoneNumber_tooShortIsRejected() {
        Member m = validMember();
        m.setPhoneNumber("123456789");
        assertThat(violationsFor(m)).isNotEmpty();
    }

    // Gap #9 — phoneNumber too long (>12) rejected
    @Test
    void phoneNumber_tooLongIsRejected() {
        Member m = validMember();
        m.setPhoneNumber("1234567890123");
        assertThat(violationsFor(m)).isNotEmpty();
    }

    @Test
    void validMember_hasNoViolations() {
        assertThat(violationsFor(validMember())).isEmpty();
    }
}
