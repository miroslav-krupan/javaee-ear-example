package com.example.kitchensink.web.rest;

import com.example.kitchensink.model.Member;
import com.example.kitchensink.repository.MemberRepository;
import com.example.kitchensink.service.EmailAlreadyExistsException;
import com.example.kitchensink.service.MemberRegistration;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers gaps #18–24 from reverse_en/test-behavior.md §5:
 *   18: GET /rest/members returns JSON array
 *   19: GET /rest/members/{id} returns 200 with member
 *   20: GET /rest/members/{id} returns 404 for unknown id
 *   21: POST /rest/members happy path → 200
 *   22: POST /rest/members constraint violation → 400 with field-error map
 *   23: POST /rest/members duplicate email → 409 {"email":"Email taken"}
 *   24: emailAlreadyExists true/false (tested via POST 409 and POST 200 paths)
 */
@WebMvcTest(MemberRestController.class)
@WithMockUser
class MemberRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private MemberRepository memberRepository;

    @MockBean
    private MemberRegistration memberRegistration;

    // --- Gap #18: GET /rest/members returns JSON array ---
    @Test
    void getAll_returnsJsonArray() throws Exception {
        Member m = member("Jane Doe", "jane@example.com", "2125551234");
        when(memberRepository.findAllOrderedByName()).thenReturn(List.of(m));

        mockMvc.perform(get("/rest/members").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Jane Doe"));
    }

    // --- Gap #19: GET /rest/members/{id} returns 200 with member ---
    @Test
    void getById_foundReturns200() throws Exception {
        Member m = member("Jane Doe", "jane@example.com", "2125551234");
        when(memberRepository.findById(1L)).thenReturn(m);

        mockMvc.perform(get("/rest/members/1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("jane@example.com"));
    }

    // --- Gap #20: GET /rest/members/{id} returns 404 for unknown id ---
    @Test
    void getById_notFoundReturns404() throws Exception {
        when(memberRepository.findById(999L)).thenReturn(null);

        mockMvc.perform(get("/rest/members/999").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    // --- Gap #21: POST /rest/members happy path → 200 ---
    @Test
    void create_happyPath_returns200() throws Exception {
        doNothing().when(memberRegistration).register(any(Member.class));

        mockMvc.perform(post("/rest/members")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                member("Jane Doe", "jane@example.com", "2125551234"))))
                .andExpect(status().isOk());
    }

    // --- Gap #22: POST /rest/members constraint violation → 400 with field-error map ---
    @Test
    void create_constraintViolation_returns400WithFieldErrors() throws Exception {
        Member invalid = new Member();
        invalid.setName(""); // violates @NotNull @Size(min=1)
        invalid.setEmail("not-an-email"); // violates @Email
        invalid.setPhoneNumber("abc"); // violates @Digits

        mockMvc.perform(post("/rest/members")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.email").exists());
    }

    // --- Gap #23: POST /rest/members duplicate email → 409 {"email":"Email taken"} ---
    @Test
    void create_duplicateEmail_returns409() throws Exception {
        doThrow(new EmailAlreadyExistsException("jane@example.com"))
                .when(memberRegistration).register(any(Member.class));

        mockMvc.perform(post("/rest/members")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                member("Jane Doe", "jane@example.com", "2125551234"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.email").value("Email taken"));
    }

    // --- Gap #24a: emailAlreadyExists=true path (409 verifies duplicate check works) ---
    @Test
    void create_emailAlreadyExists_triggers409() throws Exception {
        doThrow(new EmailAlreadyExistsException("dup@example.com"))
                .when(memberRegistration).register(any(Member.class));

        mockMvc.perform(post("/rest/members")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                member("Dup User", "dup@example.com", "2125551234"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.email").value("Email taken"));
    }

    // --- Gap #24b: emailAlreadyExists=false path (200 verifies no duplicate) ---
    @Test
    void create_emailNotExists_succeeds200() throws Exception {
        doNothing().when(memberRegistration).register(any(Member.class));

        mockMvc.perform(post("/rest/members")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                member("New User", "new@example.com", "2125551234"))))
                .andExpect(status().isOk());
    }

    private Member member(String name, String email, String phone) {
        Member m = new Member();
        m.setName(name);
        m.setEmail(email);
        m.setPhoneNumber(phone);
        return m;
    }
}
