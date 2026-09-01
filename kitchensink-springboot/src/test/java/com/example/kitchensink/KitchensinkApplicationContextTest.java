package com.example.kitchensink;

import com.example.kitchensink.service.MemberRegistration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

// Gate: proves the full Spring context loads cleanly and MemberRegistration wires up.
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:ctxtest;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.flyway.enabled=true"
})
class KitchensinkApplicationContextTest {

    @Autowired
    private MemberRegistration memberRegistration;

    @Test
    void contextLoadsAndMemberRegistrationWiresUp() {
        assertThat(memberRegistration).isNotNull();
    }
}
