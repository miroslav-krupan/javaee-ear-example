package com.example.kitchensink.api;

import com.example.kitchensink.domain.Member;
import com.example.kitchensink.repository.MemberRepository;
import com.example.kitchensink.service.MemberRegistrationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberRestController.class)
class MemberRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MemberRepository memberRepository;

    @MockBean
    private MemberRegistrationService memberRegistrationService;

    private Member validMember() {
        Member m = new Member();
        m.setId(1L);
        m.setName("Jane Doe");
        m.setEmail("jane@example.com");
        m.setPhoneNumber("2125551234");
        return m;
    }

    @Test
    void postValidMember_returns200() throws Exception {
        Member member = validMember();
        when(memberRepository.findByEmail(member.getEmail())).thenReturn(Optional.empty());

        mockMvc.perform(post("/rest/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(member)))
                .andExpect(status().isOk());
        verify(memberRegistrationService).register(any(Member.class));
    }

    @Test
    void postInvalidMember_returns400WithFieldMap() throws Exception {
        Member member = new Member();
        member.setName("Jane123");
        member.setEmail("not-an-email");
        member.setPhoneNumber("abc");

        mockMvc.perform(post("/rest/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(member)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").exists());
    }

    @Test
    void postDuplicateEmail_returns409() throws Exception {
        Member member = validMember();
        when(memberRepository.findByEmail(member.getEmail())).thenReturn(Optional.of(member));

        mockMvc.perform(post("/rest/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(member)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.email").value("Email taken"));
    }

    @Test
    void getMemberByIdNotFound_returns404() throws Exception {
        when(memberRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/rest/members/99"))
                .andExpect(status().isNotFound());
    }
}
