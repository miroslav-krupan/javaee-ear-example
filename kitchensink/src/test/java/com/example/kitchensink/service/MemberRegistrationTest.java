package com.example.kitchensink.service;

import com.example.kitchensink.data.MemberListProducer;
import com.example.kitchensink.event.MemberRegisteredEvent;
import com.example.kitchensink.model.Member;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// Migrates MemberRegistrationIT (ejb/src/test/.../test/MemberRegistrationIT.java):
//   testRegister: assertion 1:1 (assertNotNull on id after register).
// New tests cover gap items #12 and #13 from reverse_en/test-behavior.md §3.
@SpringBootTest
@RecordApplicationEvents
@Transactional
class MemberRegistrationTest {

    @Autowired
    private MemberRegistration memberRegistration;

    @Autowired
    private ApplicationEvents applicationEvents;

    @Autowired
    private MemberListProducer memberListProducer;

    // 1:1 migration of MemberRegistrationIT.testRegister()
    @Test
    void testRegister() throws Exception {
        Member member = new Member();
        member.setName("Jane Doe");
        member.setEmail("jane@mailinator.com");
        member.setPhoneNumber("2125551234");
        memberRegistration.register(member);
        assertNotNull(member.getId());
    }

    // Gap #12: CDI Event<Member>.fire() → ApplicationEventPublisher.publishEvent(MemberRegisteredEvent)
    @Test
    void registerPublishesMemberRegisteredEvent() throws Exception {
        Member member = new Member();
        member.setName("John Doe");
        member.setEmail("john@mailinator.com");
        member.setPhoneNumber("2125551235");
        memberRegistration.register(member);
        assertThat(applicationEvents.stream(MemberRegisteredEvent.class).count()).isEqualTo(1);
        assertThat(applicationEvents.stream(MemberRegisteredEvent.class)
                .map(MemberRegisteredEvent::getMember)
                .findFirst()
                .orElseThrow())
                .isSameAs(member);
    }

    // Gap #13: MemberListProducer refreshes its member list when MemberRegisteredEvent is received
    @Test
    void memberListRefreshesAfterRegistration() throws Exception {
        Member member = new Member();
        member.setName("Alice Smith");
        member.setEmail("alice@mailinator.com");
        member.setPhoneNumber("2125551236");
        memberRegistration.register(member);
        assertThat(memberListProducer.getMembers())
                .extracting(Member::getEmail)
                .contains("alice@mailinator.com");
    }
}
