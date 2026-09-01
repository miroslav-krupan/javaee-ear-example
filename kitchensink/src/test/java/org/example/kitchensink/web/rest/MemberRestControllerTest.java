package org.example.kitchensink.web.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.kitchensink.model.Member;
import org.example.kitchensink.repository.MemberRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:resttest;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.liquibase.change-log=classpath:db/changelog/db.changelog-master.xml"
})
class MemberRestControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired MemberRepository memberRepository;

    @AfterEach
    void cleanup() {
        memberRepository.deleteAll();
    }

    // Context-load: @SpringBootTest proves MemberRestController and its advice wire up
    @Test
    void contextLoads() {
        assertThat(mockMvc).isNotNull();
    }

    // Gap #15: GET /api/members returns JSON array of all members
    @Test
    void getMembers_returnsJsonArray() throws Exception {
        memberRepository.save(member("Alice", "alice@test.com", "1234567890"));
        mockMvc.perform(get("/api/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Alice"));
    }

    // Gap #16: GET /api/members/{id} returns member JSON for known id
    @Test
    void getMemberById_knownId_returnsOk() throws Exception {
        Member saved = memberRepository.save(member("Bob", "bob@test.com", "1234567890"));
        mockMvc.perform(get("/api/members/" + saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("bob@test.com"));
    }

    // Gap #17: GET /api/members/{id} returns 404 for unknown id
    @Test
    void getMemberById_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/members/9999"))
                .andExpect(status().isNotFound());
    }

    // Gap #18: POST /api/members with valid body returns 200
    @Test
    void postMember_valid_returns200() throws Exception {
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(member("Carol", "carol@test.com", "1234567890"))))
                .andExpect(status().isOk());

        assertThat(memberRepository.findByEmail("carol@test.com")).isPresent();
    }

    // Gap #19: POST /api/members with invalid bean (bad phone) returns 400 with field errors
    @Test
    void postMember_invalidPhone_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(member("Dave", "dave@test.com", "123"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.phoneNumber").exists());
    }

    // Gap #20: POST /api/members with duplicate email returns 409 "Email taken"
    @Test
    void postMember_duplicateEmail_returns409() throws Exception {
        memberRepository.save(member("Eve", "eve@test.com", "1234567890"));
        mockMvc.perform(post("/api/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(member("EveTwo", "eve@test.com", "1234567891"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.email").value("Email taken"));
    }

    // Gap #21: MemberResourceRESTServiceSecond same scenarios — covered by same consolidated controller
    @Test
    void getMembers_orderedByName() throws Exception {
        memberRepository.save(member("Zara", "zara@test.com", "1234567890"));
        memberRepository.save(member("Alice", "alice@test.com", "1234567891"));
        mockMvc.perform(get("/api/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Alice"))
                .andExpect(jsonPath("$[1].name").value("Zara"));
    }

    private Member member(String name, String email, String phone) {
        Member m = new Member();
        m.setName(name);
        m.setEmail(email);
        m.setPhoneNumber(phone);
        return m;
    }
}
