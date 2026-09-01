package com.example.kitchensink;

import com.example.kitchensink.service.MemberRegistration;
import com.example.kitchensink.web.rest.MemberRestController;
import com.example.kitchensink.web.ui.MemberController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

// Gate: proves the full Spring context loads cleanly and all controllers wire up.
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

    @Autowired
    private MemberRestController memberRestController;

    @Autowired
    private MemberController memberController;

    @Test
    void contextLoadsAndBeansWireUp() {
        assertThat(memberRegistration).isNotNull();
        assertThat(memberRestController).isNotNull();
        assertThat(memberController).isNotNull();
    }
}
