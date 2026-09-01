package com.example.kitchensink.web.ui;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Gate: @SpringBootTest context-load proving both Thymeleaf UI controllers wire up cleanly.
 * Required by frontend-specialist done-gate before commit.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class MemberControllerContextLoadTest {

    @Autowired
    private MemberController memberController;

    @Autowired
    private MemberControllerV2 memberControllerV2;

    @Test
    void memberControllerWiresUp() {
        assertThat(memberController).isNotNull();
    }

    @Test
    void memberControllerV2WiresUp() {
        assertThat(memberControllerV2).isNotNull();
    }
}
