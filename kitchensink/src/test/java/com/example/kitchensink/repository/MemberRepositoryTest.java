package com.example.kitchensink.repository;

import com.example.kitchensink.domain.Member;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class MemberRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    private Member validMember(String name, String email, String phone) {
        Member m = new Member();
        m.setName(name);
        m.setEmail(email);
        m.setPhoneNumber(phone);
        return m;
    }

    @Test
    void testRegister() {
        Member saved = memberRepository.save(validMember("Jane Doe", "jane@mailinator.com", "2125551234"));
        assertNotNull(saved.getId());
    }

    @Test
    void testFindByEmail_found() {
        memberRepository.save(validMember("Jane Doe", "jane@mailinator.com", "2125551234"));
        Optional<Member> found = memberRepository.findByEmail("jane@mailinator.com");
        assertTrue(found.isPresent());
        assertEquals("Jane Doe", found.get().getName());
    }

    @Test
    void testFindByEmail_notFound() {
        Optional<Member> found = memberRepository.findByEmail("nobody@example.com");
        assertFalse(found.isPresent());
    }

    @Test
    void testFindAllOrderedByName() {
        memberRepository.save(validMember("Zara Smith", "zara@example.com", "1234567890"));
        memberRepository.save(validMember("Alice Jones", "alice@example.com", "0987654321"));

        List<Member> all = memberRepository.findAllByOrderByNameAsc();
        assertEquals(2, all.size());
        assertEquals("Alice Jones", all.get(0).getName());
        assertEquals("Zara Smith", all.get(1).getName());
    }
}
