package com.example.kitchensink.repository;

import com.example.kitchensink.model.Member;
import jakarta.persistence.NoResultException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Gap list items #10-14: MemberRepository queries
@DataJpaTest
@Import(MemberRepository.class)
class MemberRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private MemberRepository repository;

    private Member savedMember;

    @BeforeEach
    void setUp() {
        Member m = new Member();
        m.setName("Alice");
        m.setEmail("alice@example.com");
        m.setPhoneNumber("1234567890");
        savedMember = em.persistAndFlush(m);
    }

    // Gap #10 — findById returns entity by PK
    @Test
    void findById_returnsEntityWhenFound() {
        Member found = repository.findById(savedMember.getId());
        assertThat(found).isNotNull();
        assertThat(found.getName()).isEqualTo("Alice");
        assertThat(found.getEmail()).isEqualTo("alice@example.com");
    }

    // Gap #11 — findById returns null for unknown PK
    @Test
    void findById_returnsNullWhenNotFound() {
        Member found = repository.findById(-999L);
        assertThat(found).isNull();
    }

    // Gap #12 — findByEmail returns entity by email
    @Test
    void findByEmail_returnsEntityWhenFound() {
        Member found = repository.findByEmail("alice@example.com");
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(savedMember.getId());
    }

    // Gap #13 — findByEmail throws NoResultException for unknown email
    @Test
    void findByEmail_throwsNoResultExceptionWhenNotFound() {
        assertThatThrownBy(() -> repository.findByEmail("notfound@example.com"))
                .isInstanceOf(NoResultException.class);
    }

    // Gap #14 — findAllOrderedByName returns list sorted by name ascending
    @Test
    void findAllOrderedByName_returnsSortedByNameAscending() {
        Member m2 = new Member();
        m2.setName("Bob");
        m2.setEmail("bob@example.com");
        m2.setPhoneNumber("0987654321");
        em.persistAndFlush(m2);

        Member m3 = new Member();
        m3.setName("Aaron");
        m3.setEmail("aaron@example.com");
        m3.setPhoneNumber("1112223333");
        em.persistAndFlush(m3);

        List<Member> members = repository.findAllOrderedByName();
        assertThat(members).extracting(Member::getName)
                .containsExactly("Aaron", "Alice", "Bob");
    }
}
