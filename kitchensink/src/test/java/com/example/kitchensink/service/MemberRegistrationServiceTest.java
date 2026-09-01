package com.example.kitchensink.service;

import com.example.kitchensink.domain.Member;
import com.example.kitchensink.web.MemberListModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
                "spring.jpa.hibernate.ddl-auto=create-drop"
        })
@RecordApplicationEvents
@Transactional
class MemberRegistrationServiceTest {

    @MockBean
    @SuppressWarnings("unused")
    private MemberListModel memberListModel;

    @Autowired
    private MemberRegistrationService memberRegistrationService;

    @Autowired
    private ApplicationEvents applicationEvents;

    @Test
    void testRegister() {
        Member newMember = new Member();
        newMember.setName("Jane Doe");
        newMember.setEmail("jane@mailinator.com");
        newMember.setPhoneNumber("2125551234");
        memberRegistrationService.register(newMember);
        assertNotNull(newMember.getId());
    }

    @Test
    void testRegister_publishesMemberRegisteredEvent() {
        Member newMember = new Member();
        newMember.setName("Jane Doe");
        newMember.setEmail("jane@mailinator.com");
        newMember.setPhoneNumber("2125551234");
        memberRegistrationService.register(newMember);
        assertEquals(1L, applicationEvents.stream(MemberRegisteredEvent.class).count());
    }
}
