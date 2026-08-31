package com.example.kitchensink.web.primary;

import com.example.kitchensink.model.Member;
import com.example.kitchensink.repository.MemberRepository;
import com.example.kitchensink.service.MemberRegistration;
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

// Covers gap items #8, #9, #10, #11 from reverse_en/test-behavior.md §3.
@WebMvcTest(MemberResourceRESTService.class)
class MemberResourceRESTServiceTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MemberRepository repository;

    @MockBean
    private MemberRegistration registration;

    // Gap #8: GET /members/{id} not found → 404
    @Test
    void lookupMemberById_notFound_returns404() throws Exception {
        when(repository.findById(999L)).thenReturn(Optional.empty());
        mockMvc.perform(get("/web/rest/members/999"))
                .andExpect(status().isNotFound());
    }

    // Gap #9: POST with invalid fields → 400 with field→message map
    @Test
    void createMember_invalidFields_returns400WithFieldMap() throws Exception {
        // name "John123" violates @Pattern(regexp="[^0-9]*") — must not contain numbers
        String invalidJson = """
                {"name":"John123","email":"john@example.com","phoneNumber":"2125551234"}
                """;
        mockMvc.perform(post("/web/rest/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").exists());
    }

    // Gap #10: POST with duplicate email → 409 {"email": "Email taken"}
    @Test
    void createMember_duplicateEmail_returns409() throws Exception {
        Member existing = new Member();
        existing.setName("Existing");
        existing.setEmail("dupe@example.com");
        existing.setPhoneNumber("2125551234");
        when(repository.findByEmailOptional("dupe@example.com")).thenReturn(Optional.of(existing));

        String json = """
                {"name":"Jane Doe","email":"dupe@example.com","phoneNumber":"2125551234"}
                """;
        mockMvc.perform(post("/web/rest/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.email").value("Email taken"));
    }

    // Gap #11: POST happy path → 200
    @Test
    void createMember_valid_returns200() throws Exception {
        when(repository.findByEmailOptional("new@example.com")).thenReturn(Optional.empty());

        String json = """
                {"name":"Jane Doe","email":"new@example.com","phoneNumber":"2125551234"}
                """;
        mockMvc.perform(post("/web/rest/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
        verify(registration).register(any(Member.class));
    }
}
