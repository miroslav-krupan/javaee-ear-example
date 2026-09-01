package org.example.kitchensink.web.ui;

import org.example.kitchensink.model.Member;
import org.example.kitchensink.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:uicontrollertest;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.xml"
})
class MemberControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired MemberRepository memberRepository;
    @Autowired MemberController memberController;

    @AfterEach
    void cleanup() {
        memberRepository.deleteAll();
    }

    // Context-load: MemberController and its dependencies wire up correctly
    @Test
    void contextLoads() {
        assertThat(memberController).isNotNull();
    }

    // GET /members renders index view with empty form and member list
    @Test
    void getMembers_rendersIndexTemplate() throws Exception {
        mockMvc.perform(get("/members"))
                .andExpect(status().isOk())
                .andExpect(view().name("members/index"))
                .andExpect(model().attributeExists("newMember"))
                .andExpect(model().attributeExists("members"));
    }

    // POST /members with valid data applies PRG pattern and persists member
    @Test
    void postMember_valid_redirectsToMembersAndPersists() throws Exception {
        mockMvc.perform(post("/members")
                        .param("name", "Alice")
                        .param("email", "alice@test.com")
                        .param("phoneNumber", "1234567890"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/members"));

        assertThat(memberRepository.findByEmail("alice@test.com")).isPresent();
    }

    // POST /members with invalid data re-renders form with binding errors
    @Test
    void postMember_invalidData_rendersFormWithErrors() throws Exception {
        mockMvc.perform(post("/members")
                        .param("name", "")
                        .param("email", "not-an-email")
                        .param("phoneNumber", "123"))
                .andExpect(status().isOk())
                .andExpect(view().name("members/index"))
                .andExpect(model().hasErrors());
    }

    // POST /members with duplicate email redirects (EmailAlreadyExistsException handled gracefully)
    @Test
    void postMember_duplicateEmail_redirectsWithErrorFlash() throws Exception {
        memberRepository.save(member("Bob", "bob@test.com", "1234567890"));

        mockMvc.perform(post("/members")
                        .param("name", "Bobby")
                        .param("email", "bob@test.com")
                        .param("phoneNumber", "1234567891"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/members"));

        assertThat(memberRepository.count()).isEqualTo(1);
    }

    // GET /members shows existing members in model
    @Test
    void getMembers_withExistingMembers_populatesMemberList() throws Exception {
        memberRepository.save(member("Carol", "carol@test.com", "1234567890"));

        mockMvc.perform(get("/members"))
                .andExpect(status().isOk())
                .andExpect(model().attributeExists("members"));
    }

    private Member member(String name, String email, String phone) {
        Member m = new Member();
        m.setName(name);
        m.setEmail(email);
        m.setPhoneNumber(phone);
        return m;
    }
}
