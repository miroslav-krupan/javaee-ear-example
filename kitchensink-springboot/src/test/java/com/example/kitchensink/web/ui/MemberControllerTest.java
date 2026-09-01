package com.example.kitchensink.web.ui;

import com.example.kitchensink.domain.Member;
import com.example.kitchensink.repository.MemberRepository;
import com.example.kitchensink.service.MemberRegistration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Gaps 23-24: MemberController (JSF @Model → Spring @Controller + Thymeleaf)
@WebMvcTest(MemberController.class)
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MemberRegistration memberRegistration;

    @MockBean
    private MemberRepository memberRepository;

    // Gap 23: register success adds INFO message and resets form (redirect + flash)
    @Test
    void register_success_redirectsWithFlashMessage() throws Exception {
        given(memberRepository.findByEmail("jane@mailinator.com")).willReturn(Optional.empty());
        given(memberRepository.findAllByOrderByNameAsc()).willReturn(List.of());

        mockMvc.perform(post("/register")
                        .param("name", "Jane Doe")
                        .param("email", "jane@mailinator.com")
                        .param("phoneNumber", "2125551234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("successMessage", "Registered!"));
    }

    // Gap 24: register exception adds ERROR message with root cause message
    @Test
    void register_exception_addsErrorMessageToModel() throws Exception {
        given(memberRepository.findByEmail("jane@mailinator.com")).willReturn(Optional.empty());
        given(memberRepository.findAllByOrderByNameAsc()).willReturn(List.of());
        doThrow(new RuntimeException("db connection lost")).when(memberRegistration).register(any());

        mockMvc.perform(post("/register")
                        .param("name", "Jane Doe")
                        .param("email", "jane@mailinator.com")
                        .param("phoneNumber", "2125551234"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("errorMessage"));
    }

    @Test
    void register_validationError_returnsFormWithErrors() throws Exception {
        given(memberRepository.findAllByOrderByNameAsc()).willReturn(List.of());

        mockMvc.perform(post("/register")
                        .param("name", "Jane123")
                        .param("email", "jane@mailinator.com")
                        .param("phoneNumber", "2125551234"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeHasFieldErrors("newMember", "name"));
    }

    @Test
    void register_duplicateEmail_returns409FieldError() throws Exception {
        Member existing = new Member();
        given(memberRepository.findByEmail("jane@mailinator.com")).willReturn(Optional.of(existing));
        given(memberRepository.findAllByOrderByNameAsc()).willReturn(List.of());

        mockMvc.perform(post("/register")
                        .param("name", "Jane Doe")
                        .param("email", "jane@mailinator.com")
                        .param("phoneNumber", "2125551234"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeHasFieldErrors("newMember", "email"));
    }

    @Test
    void showRegistrationForm_returnsIndexWithEmptyMember() throws Exception {
        given(memberRepository.findAllByOrderByNameAsc()).willReturn(List.of());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("newMember"))
                .andExpect(model().attributeExists("members"));
    }
}
