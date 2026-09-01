package org.example.kitchensink.repository;

import jakarta.transaction.Transactional;
import org.example.kitchensink.model.Member;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Validates both repository behavior and Liquibase schema correctness:
 * ddl-auto=validate means Hibernate must confirm the schema created by Liquibase
 * matches the entity mappings exactly before any test runs.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.xml",
    "spring.thymeleaf.check-template-location=false"
})
@Transactional
class MemberRepositoryTest {

    @Autowired
    private MemberRepository repository;

    @Test
    void savePersistsAndAssignsId() {
        Member saved = repository.saveAndFlush(member("Jane Doe", "jane@example.com", "2125551234"));
        assertThat(saved.getId()).isNotNull();
    }

    @Test
    void findByEmailReturnsPresent() {
        repository.saveAndFlush(member("Jane Doe", "jane@example.com", "2125551234"));
        Optional<Member> found = repository.findByEmail("jane@example.com");
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("jane@example.com");
    }

    @Test
    void findByEmailReturnsEmptyForUnknown() {
        assertThat(repository.findByEmail("nobody@example.com")).isEmpty();
    }

    @Test
    void findAllByOrderByNameAscReturnsSorted() {
        repository.saveAndFlush(member("Zara Smith", "zara@example.com", "2125551234"));
        repository.saveAndFlush(member("Alice Jones", "alice@example.com", "2125551235"));
        List<Member> members = repository.findAllByOrderByNameAsc();
        assertThat(members).extracting(Member::getName)
            .containsExactly("Alice Jones", "Zara Smith");
    }

    private Member member(String name, String email, String phone) {
        Member m = new Member();
        m.setName(name);
        m.setEmail(email);
        m.setPhoneNumber(phone);
        return m;
    }
}
