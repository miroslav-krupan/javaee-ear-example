package com.example.kitchensink.web;

import com.example.kitchensink.domain.Member;
import com.example.kitchensink.service.MemberRegistrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(MemberController.class)
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MemberRegistrationService memberRegistrationService;

    @MockBean
    private MemberListModel memberListModel;

    @Test
    void indexPage_rendersWithMemberList() throws Exception {
        when(memberListModel.getMembers()).thenReturn(List.of());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("members", "newMember"));
    }

    @Test
    void registerSuccess_redirectsWithSuccessMessage() throws Exception {
        mockMvc.perform(post("/members/register")
                        .param("name", "Jane Doe")
                        .param("email", "jane@example.com")
                        .param("phoneNumber", "2125551234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("successMessage", "Registered!"));
        verify(memberRegistrationService).register(any(Member.class));
    }

    @Test
    void registerFailure_redirectsWithRootCauseErrorMessage() throws Exception {
        RuntimeException cause = new RuntimeException("leaf cause");
        RuntimeException wrapper = new RuntimeException("wrapper", cause);
        doThrow(wrapper).when(memberRegistrationService).register(any(Member.class));

        mockMvc.perform(post("/members/register")
                        .param("name", "Jane Doe")
                        .param("email", "jane@example.com")
                        .param("phoneNumber", "2125551234"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(flash().attribute("errorMessage", "leaf cause"));
    }
}
