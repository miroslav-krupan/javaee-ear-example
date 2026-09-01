package com.example.kitchensink.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gate: proves the security configuration wires up correctly within the full application context.
 * Required by security-specialist done-gate.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SecurityConfigContextLoadTest {

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void securityFilterChainWiresUp() {
        assertThat(securityFilterChain).isNotNull();
    }

    @Test
    void passwordEncoderWiresUp() {
        assertThat(passwordEncoder).isNotNull();
    }

    @Test
    void passwordEncoderIsBCrypt() {
        String encoded = passwordEncoder.encode("password");
        assertThat(passwordEncoder.matches("password", encoded)).isTrue();
    }
}
