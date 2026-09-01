package com.example.kitchensink.web.rest;

import com.example.kitchensink.domain.Member;
import com.example.kitchensink.repository.MemberRepository;
import com.example.kitchensink.service.MemberRegistration;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Gaps 16-22: MemberResourceRESTService → MemberRestController
// Gap 26:     MemberResourceRESTServiceSecond merged — covered by the same controller
@WebMvcTest(MemberRestController.class)
class MemberRestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private Validator validator;

    @MockBean
    private MemberRepository memberRepository;

    @MockBean
    private MemberRegistration registration;

    private Member jane;

    @BeforeEach
    void setUp() {
        jane = new Member();
        jane.setName("Jane Doe");
        jane.setEmail("jane@mailinator.com");
        jane.setPhoneNumber("2125551234");
    }

    // Gap 16: GET /rest/members returns all members as JSON list
    @Test
    void listAllMembers_returnsJsonList() throws Exception {
        given(memberRepository.findAllByOrderByNameAsc()).willReturn(List.of(jane));

        mockMvc.perform(get("/rest/members").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Jane Doe"));
    }

    // Gap 17: GET /rest/members/{id} returns 404 when member not found
    @Test
    void lookupById_notFound_returns404() throws Exception {
        given(memberRepository.findById(99L)).willReturn(Optional.empty());

        mockMvc.perform(get("/rest/members/99").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    void lookupById_found_returns200WithMember() throws Exception {
        given(memberRepository.findById(1L)).willReturn(Optional.of(jane));

        mockMvc.perform(get("/rest/members/1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jane Doe"));
    }

    // Gap 18: POST /rest/members with valid payload → 200 OK
    @Test
    void createMember_validPayload_returns200() throws Exception {
        given(validator.validate(any(Member.class))).willReturn(Set.of());
        given(memberRepository.findByEmail("jane@mailinator.com")).willReturn(Optional.empty());

        String json = """
                {"name":"Jane Doe","email":"jane@mailinator.com","phoneNumber":"2125551234"}
                """;

        mockMvc.perform(post("/rest/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
    }

    // Gap 19: POST /rest/members with invalid payload → 400 with violation map
    @Test
    @SuppressWarnings("unchecked")
    void createMember_invalidPayload_returns400WithViolationMap() throws Exception {
        ConstraintViolation<Member> violation = mockViolation("name", "Must not contain numbers");
        ConstraintViolationException cve = new ConstraintViolationException(Set.of(violation));
        given(validator.validate(any(Member.class))).willThrow(cve);

        String json = """
                {"name":"Jane123","email":"jane@mailinator.com","phoneNumber":"2125551234"}
                """;

        mockMvc.perform(post("/rest/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name").value("Must not contain numbers"));
    }

    // Gap 20: POST /rest/members with duplicate email → 409 {"email":"Email taken"}
    @Test
    void createMember_duplicateEmail_returns409() throws Exception {
        given(validator.validate(any(Member.class))).willReturn(Set.of());
        given(memberRepository.findByEmail("jane@mailinator.com")).willReturn(Optional.of(jane));

        String json = """
                {"name":"Jane Doe","email":"jane@mailinator.com","phoneNumber":"2125551234"}
                """;

        mockMvc.perform(post("/rest/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.email").value("Email taken"));
    }

    // Gap 21: POST /rest/members generic exception → 400 with "error" key
    @Test
    void createMember_genericException_returns400WithErrorKey() throws Exception {
        given(validator.validate(any(Member.class))).willReturn(Set.of());
        given(memberRepository.findByEmail(any())).willReturn(Optional.empty());
        doThrow(new RuntimeException("something broke")).when(registration).register(any());

        String json = """
                {"name":"Jane Doe","email":"jane@mailinator.com","phoneNumber":"2125551234"}
                """;

        mockMvc.perform(post("/rest/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("something broke"));
    }

    // Gap 22: emailAlreadyExists returns true on duplicate, false otherwise
    @Test
    void emailAlreadyExists_trueWhenPresent_falseWhenAbsent() {
        MemberRestController controller = new MemberRestController(validator, memberRepository, registration);

        given(memberRepository.findByEmail("taken@example.com")).willReturn(Optional.of(jane));
        given(memberRepository.findByEmail("free@example.com")).willReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThat(controller.emailAlreadyExists("taken@example.com")).isTrue();
        org.assertj.core.api.Assertions.assertThat(controller.emailAlreadyExists("free@example.com")).isFalse();
    }

    @SuppressWarnings("unchecked")
    private <T> ConstraintViolation<T> mockViolation(String propertyPath, String message) {
        ConstraintViolation<T> v = org.mockito.Mockito.mock(ConstraintViolation.class);
        Path path = org.mockito.Mockito.mock(Path.class);
        given(path.toString()).willReturn(propertyPath);
        given(v.getPropertyPath()).willReturn(path);
        given(v.getMessage()).willReturn(message);
        return v;
    }
}
