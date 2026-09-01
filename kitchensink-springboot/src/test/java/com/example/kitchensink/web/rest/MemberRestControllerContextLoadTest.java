package com.example.kitchensink.web.rest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gate: @SpringBootTest context-load proving both REST controllers wire up cleanly.
 * Required by sync-comm-specialist done-gate before commit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MemberRestControllerContextLoadTest {

    @Autowired
    private MemberRestController memberRestController;

    @Autowired
    private MemberRestControllerV2 memberRestControllerV2;

    @Test
    void memberRestControllerWiresUp() {
        assertThat(memberRestController).isNotNull();
    }

    @Test
    void memberRestControllerV2WiresUp() {
        assertThat(memberRestControllerV2).isNotNull();
    }
}
