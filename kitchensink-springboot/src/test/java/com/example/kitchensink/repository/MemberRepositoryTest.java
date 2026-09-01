package com.example.kitchensink.repository;

import com.example.kitchensink.domain.Member;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

// Schema validation gate: Flyway creates the schema, Hibernate validates — proves entity ↔ DDL alignment.
// Replace.NONE keeps the full autoconfiguration chain (including FlywayAutoConfiguration).
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.enabled=true"
})
class MemberRepositoryTest {

    @Autowired
    private MemberRepository repository;

    private Member newMember(String name, String email, String phone) {
        Member m = new Member();
        m.setName(name);
        m.setEmail(email);
        m.setPhoneNumber(phone);
        return m;
    }

    // Gap 11: findByEmail returns correct member on match
    @Test
    void findByEmailReturnsMatchingMember() {
        repository.save(newMember("Alice Smith", "alice@example.com", "5551234567"));
        Optional<Member> result = repository.findByEmail("alice@example.com");
        assertThat(result).isPresent()
                          .get()
                          .extracting(Member::getEmail)
                          .isEqualTo("alice@example.com");
    }

    // Gap 12: findByEmail returns empty when email not found
    @Test
    void findByEmailReturnsEmptyWhenNotFound() {
        Optional<Member> result = repository.findByEmail("nobody@example.com");
        assertThat(result).isEmpty();
    }

    // Gap 13: findAllOrderedByName returns list in ascending name order
    @Test
    void findAllByOrderByNameAscReturnsMembersInOrder() {
        repository.save(newMember("Zara Jones", "zara@example.com", "5550000001"));
        repository.save(newMember("Alice Brown", "alice2@example.com", "5550000002"));
        repository.save(newMember("Mark Lee", "mark@example.com", "5550000003"));
        List<Member> members = repository.findAllByOrderByNameAsc();
        assertThat(members).extracting(Member::getName)
                           .containsExactly("Alice Brown", "Mark Lee", "Zara Jones");
    }

    // Gap 14: findById returns Optional.empty() when id not found
    @Test
    void findByIdReturnsEmptyWhenNotFound() {
        Optional<Member> result = repository.findById(Long.MAX_VALUE);
        assertThat(result).isEmpty();
    }

    // Verifies persist and retrieve round-trip (positive findById case)
    @Test
    void savedMemberIsRetrievableById() {
        Member saved = repository.save(newMember("Test User", "test@example.com", "5551111111"));
        assertThat(saved.getId()).isNotNull();
        Optional<Member> found = repository.findById(saved.getId());
        assertThat(found).isPresent()
                         .get()
                         .extracting(Member::getName)
                         .isEqualTo("Test User");
    }
}
