package com.example.kitchensink.web;

import com.example.kitchensink.service.MemberRegistrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class MemberControllerRootMessageTest {

    @Mock
    private MemberRegistrationService memberRegistrationService;
    @Mock
    private MemberListModel memberListModel;

    private MemberController controller;

    @BeforeEach
    void setUp() {
        controller = new MemberController("test-config", memberRegistrationService, memberListModel);
    }

    @Test
    void nullException_returnsDefaultMessage() {
        assertThat(controller.getRootErrorMessage(null))
                .isEqualTo("Registration failed. See server log for more information");
    }

    @Test
    void singleException_returnsItsMessage() {
        assertThat(controller.getRootErrorMessage(new RuntimeException("direct message")))
                .isEqualTo("direct message");
    }

    @Test
    void chainedExceptions_returnsLeafCauseMessage() {
        RuntimeException root = new RuntimeException("root cause");
        RuntimeException mid = new RuntimeException("mid", root);
        RuntimeException top = new RuntimeException("top", mid);
        assertThat(controller.getRootErrorMessage(top)).isEqualTo("root cause");
    }
}
