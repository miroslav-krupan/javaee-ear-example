package com.example.kitchensink;

import com.example.kitchensink.model.Member;
import com.example.kitchensink.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves entity mappings load correctly against the Flyway schema with ddl-auto=validate.
 * Flyway runs V1__init.sql; Hibernate then validates Member against the created schema.
 */
@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true"
})
@Import(MemberRepository.class)
class MemberSchemaValidationTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private MemberRepository repository;

    @Test
    void entityMappingsLoadAgainstFlywaySchema() {
        Member m = new Member();
        m.setName("Schema Test");
        m.setEmail("schema@test.com");
        m.setPhoneNumber("5559876543");
        em.persistAndFlush(m);

        assertThat(m.getId()).isNotNull();
        Member found = repository.findById(m.getId());
        assertThat(found).isNotNull();
        assertThat(found.getEmail()).isEqualTo("schema@test.com");
    }
}
