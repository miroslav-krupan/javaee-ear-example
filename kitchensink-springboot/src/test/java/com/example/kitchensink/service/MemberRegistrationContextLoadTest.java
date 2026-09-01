package com.example.kitchensink.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the MemberRegistration service wires up within the Spring application context.
 * Gate: @SpringBootTest context-load required by business-logic specialist done-gate.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MemberRegistrationContextLoadTest {

    @Autowired
    private MemberRegistration memberRegistration;

    @Test
    void memberRegistrationServiceWiresUp() {
        assertThat(memberRegistration).isNotNull();
    }
}
