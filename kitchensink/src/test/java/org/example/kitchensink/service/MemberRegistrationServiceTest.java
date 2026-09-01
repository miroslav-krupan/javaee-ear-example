package org.example.kitchensink.service;

import org.example.kitchensink.model.Member;
import org.example.kitchensink.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.event.EventListener;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:servicetest;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.xml",
    "spring.thymeleaf.check-template-location=false"
})
class MemberRegistrationServiceTest {

    @Autowired
    MemberRegistrationService service;

    @Autowired
    MemberRepository repository;

    @Autowired
    EventCaptor eventCaptor;

    @AfterEach
    void cleanup() {
        repository.deleteAll();
        eventCaptor.captured.clear();
    }

    @TestConfiguration
    static class Config {
        @Bean
        EventCaptor eventCaptor() {
            return new EventCaptor();
        }
    }

    static class EventCaptor {
        final List<MemberRegisteredEvent> captured = new ArrayList<>();

        @EventListener
        void on(MemberRegisteredEvent e) {
            captured.add(e);
        }
    }

    // Gap #10: register() persists a valid member and fires an event
    @Test
    void registerPersistsMemberAndPublishesEvent() {
        Member m = member("Jane Doe", "jane@mailinator.com", "2125551234");
        service.register(m);

        assertThat(m.getId()).isNotNull();
        assertThat(repository.findById(m.getId())).isPresent();
        assertThat(eventCaptor.captured).hasSize(1);
        assertThat(eventCaptor.captured.get(0).member().getEmail()).isEqualTo("jane@mailinator.com");
    }

    // Gap #11: register() with duplicate email causes rollback (DB unique constraint)
    @Test
    void registerWithDuplicateEmailThrowsAndRollsBack() {
        service.register(member("Jane Doe", "jane@mailinator.com", "2125551234"));

        assertThatThrownBy(() -> service.register(member("John Doe", "jane@mailinator.com", "2125551235")))
            .isInstanceOf(Exception.class);

        assertThat(repository.count()).isEqualTo(1);
        assertThat(repository.findByEmail("jane@mailinator.com")).isPresent();
    }

    // Context-load proof: service and its dependencies wire up correctly
    @Test
    void contextLoads() {
        assertThat(service).isNotNull();
        assertThat(repository).isNotNull();
    }

    private Member member(String name, String email, String phone) {
        Member m = new Member();
        m.setName(name);
        m.setEmail(email);
        m.setPhoneNumber(phone);
        return m;
    }
}
