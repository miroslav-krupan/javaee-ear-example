package com.example.kitchensink.service;

import com.example.kitchensink.model.Member;
import com.example.kitchensink.repository.MemberRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Gap #17: MemberRegistration.register() persists member and fires event.
 * Uses Mockito to avoid any container dependency.
 */
@ExtendWith(MockitoExtension.class)
class MemberRegistrationServiceTest {

    @Mock
    private EntityManager em;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private MemberRegistration memberRegistration;

    private Member member;

    @BeforeEach
    void setUp() {
        memberRegistration = new MemberRegistration(memberRepository, eventPublisher);
        ReflectionTestUtils.setField(memberRegistration, "em", em);
        member = new Member();
        member.setName("Jane Doe");
        member.setEmail("jane@example.com");
        member.setPhoneNumber("2125551234");
    }

    @Test
    void register_persistsMemberAndPublishesEvent() throws Exception {
        when(memberRepository.findByEmail("jane@example.com"))
                .thenThrow(new NoResultException("not found"));

        memberRegistration.register(member);

        verify(em).persist(member);

        ArgumentCaptor<MemberRegisteredEvent> eventCaptor =
                ArgumentCaptor.forClass(MemberRegisteredEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getMember()).isSameAs(member);
    }

    @Test
    void register_throwsEmailAlreadyExistsException_whenEmailTaken() {
        Member existing = new Member();
        existing.setEmail("jane@example.com");
        when(memberRepository.findByEmail("jane@example.com")).thenReturn(existing);

        assertThatThrownBy(() -> memberRegistration.register(member))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(em, never()).persist(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
