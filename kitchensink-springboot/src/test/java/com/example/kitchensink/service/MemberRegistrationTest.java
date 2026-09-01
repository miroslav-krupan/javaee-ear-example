package com.example.kitchensink.service;

import com.example.kitchensink.domain.Member;
import com.example.kitchensink.repository.MemberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

// Gap 15: register persists entity and fires application event
@ExtendWith(MockitoExtension.class)
class MemberRegistrationTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private MemberRegistration memberRegistration;

    @BeforeEach
    void setUp() {
        memberRegistration = new MemberRegistration(memberRepository, eventPublisher);
    }

    @Test
    void registerSavesMemberAndPublishesEvent() {
        Member member = new Member();
        member.setName("Jane Doe");
        member.setEmail("jane@mailinator.com");
        member.setPhoneNumber("2125551234");

        memberRegistration.register(member);

        verify(memberRepository).save(member);

        ArgumentCaptor<MemberRegisteredEvent> eventCaptor = ArgumentCaptor.forClass(MemberRegisteredEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getMember()).isSameAs(member);
    }
}
