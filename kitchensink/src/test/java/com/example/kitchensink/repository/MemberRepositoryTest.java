package com.example.kitchensink.repository;

import jakarta.persistence.NoResultException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.IncorrectResultSizeDataAccessException;

import com.example.kitchensink.model.Member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Covers gaps #6, #7, and #14 from reverse_en/test-behavior.md §3.
// Source: ejb/src/main/java/.../data/MemberRepository.java (findById, findByEmail, getSingleResult edge cases)
@DataJpaTest
class MemberRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private TestEntityManager em;

    private Member persist(String name, String email, String phone) {
        Member m = new Member();
        m.setName(name);
        m.setEmail(email);
        m.setPhoneNumber(phone);
        return em.persistAndFlush(m);
    }

    // Gap #7: findById not-found — JpaRepository returns empty Optional (equivalent to legacy null return)
    @Test
    void findById_notFound_returnsEmpty() {
        assertThat(memberRepository.findById(Long.MAX_VALUE)).isEmpty();
    }

    // Gap #6: findByEmail not-found — must throw NoResultException (preserved from legacy getSingleResult)
    @Test
    void findByEmail_notFound_throwsNoResultException() {
        assertThatThrownBy(() -> memberRepository.findByEmail("nobody@example.com"))
                .isInstanceOf(NoResultException.class);
    }

    // findByEmail happy path — returns correct member
    @Test
    void findByEmail_found_returnsMember() {
        persist("Alice", "alice@example.com", "2125551234");
        Member found = memberRepository.findByEmail("alice@example.com");
        assertThat(found.getEmail()).isEqualTo("alice@example.com");
    }

    // findAllOrderedByName — returns members sorted ascending by name
    @Test
    void findAllOrderedByName_returnsSortedList() {
        persist("Charlie", "charlie@example.com", "2125551236");
        persist("Alice", "alice@example.com", "2125551234");
        persist("Bob", "bob@example.com", "2125551235");
        assertThat(memberRepository.findAllOrderedByName())
                .extracting(Member::getName)
                .containsExactly("Alice", "Bob", "Charlie");
    }

    // Gap #14: NonUniqueResultException propagates when duplicate email rows exist in DB.
    // The unique constraint is dropped via native SQL (H2 constraint named explicitly in entity annotation)
    // to simulate broken DB state. Spring Data JPA wraps the JPA NonUniqueResultException in
    // IncorrectResultSizeDataAccessException — the unguarded failure path still propagates unchecked.
    @Test
    void findByEmail_duplicateEmailInDb_throwsException() {
        em.getEntityManager()
                .createNativeQuery("ALTER TABLE AA_Registrant DROP CONSTRAINT IF EXISTS uq_aa_registrant_email")
                .executeUpdate();
        em.getEntityManager()
                .createNativeQuery("INSERT INTO AA_Registrant (name, email, phone_number) VALUES ('Dup1', 'dup@example.com', '2125550001')")
                .executeUpdate();
        em.getEntityManager()
                .createNativeQuery("INSERT INTO AA_Registrant (name, email, phone_number) VALUES ('Dup2', 'dup@example.com', '2125550002')")
                .executeUpdate();
        em.flush();

        assertThatThrownBy(() -> memberRepository.findByEmail("dup@example.com"))
                .isInstanceOf(IncorrectResultSizeDataAccessException.class);
    }
}
