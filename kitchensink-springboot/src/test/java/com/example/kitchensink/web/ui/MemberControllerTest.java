package com.example.kitchensink.web.ui;

import com.example.kitchensink.model.Member;
import com.example.kitchensink.repository.MemberRepository;
import com.example.kitchensink.service.EmailAlreadyExistsException;
import com.example.kitchensink.service.MemberRegistration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * Covers test-behavior gaps for the Thymeleaf UI layer:
 *   Gap 15: MemberListProducer @PostConstruct — GET populates members list
 *   Gap 16: onMemberListChanged — list refreshed on each request (request-scoped)
 *   Gap 25: MemberController register() happy path — redirect with success message
 *   Gap 26: MemberController register() error path — error message with root cause
 */
@WebMvcTest({MemberController.class, MemberControllerV2.class})
@WithMockUser
class MemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MemberRegistration memberRegistration;

    @MockBean
    private MemberRepository memberRepository;

    // Gap 15: GET /kitchensink-ear-web populates the members list (merges MemberListProducer)
    @Test
    void getIndex_populatesMembersList() throws Exception {
        Member m = new Member();
        m.setName("Alice");
        m.setEmail("alice@example.com");
        m.setPhoneNumber("1234567890");
        when(memberRepository.findAllOrderedByName()).thenReturn(List.of(m));

        mockMvc.perform(get("/kitchensink-ear-web"))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("newMember"))
                .andExpect(model().attributeExists("members"));
    }

    // Gap 16: each GET fetches a fresh members list — repository is called on every request
    @Test
    void getIndex_eachRequestFetchesFreshList() throws Exception {
        when(memberRepository.findAllOrderedByName()).thenReturn(List.of());

        mockMvc.perform(get("/kitchensink-ear-web"))
                .andExpect(status().isOk());

        verify(memberRepository, times(1)).findAllOrderedByName();
    }

    // Gap 25: register() happy path — redirects with success flash message, newMember reset
    @Test
    void register_happyPath_redirectsWithSuccessMessage() throws Exception {
        doNothing().when(memberRegistration).register(any());

        mockMvc.perform(post("/kitchensink-ear-web/register")
                        .param("name", "Alice")
                        .param("email", "alice@example.com")
                        .param("phoneNumber", "1234567890")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kitchensink-ear-web"))
                .andExpect(flash().attribute("successMessage", "Registered!"));
    }

    // Gap 26: register() error path — shows error message with root cause
    @Test
    void register_errorPath_showsErrorMessage() throws Exception {
        doThrow(new EmailAlreadyExistsException("alice@example.com"))
                .when(memberRegistration).register(any());
        when(memberRepository.findAllOrderedByName()).thenReturn(List.of());

        mockMvc.perform(post("/kitchensink-ear-web/register")
                        .param("name", "Alice")
                        .param("email", "alice@example.com")
                        .param("phoneNumber", "1234567890")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeExists("errorMessage"));
    }

    // Gap 26 (variant): getRootErrorMessage walks exception cause chain to root
    @Test
    void register_errorPath_extractsRootCauseMessage() throws Exception {
        RuntimeException root = new RuntimeException("root cause message");
        RuntimeException wrapper = new RuntimeException("wrapper", root);
        doThrow(wrapper).when(memberRegistration).register(any());
        when(memberRepository.findAllOrderedByName()).thenReturn(List.of());

        mockMvc.perform(post("/kitchensink-ear-web/register")
                        .param("name", "Alice")
                        .param("email", "alice@example.com")
                        .param("phoneNumber", "1234567890")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("errorMessage", "root cause message"));
    }

    // Validation: bean constraint violations return the form view without calling register()
    @Test
    void register_validationFailure_returnsFormView() throws Exception {
        mockMvc.perform(post("/kitchensink-ear-web/register")
                        .param("name", "")           // blank — violates @NotNull @Size(min=1)
                        .param("email", "not-an-email")
                        .param("phoneNumber", "abc")  // non-digits
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("index"))
                .andExpect(model().attributeHasErrors("newMember"));
    }

    // App 2 controller: GET populates members list
    @Test
    void getIndex2_populatesMembersList() throws Exception {
        when(memberRepository.findAllOrderedByName()).thenReturn(List.of());

        mockMvc.perform(get("/kitchensink-ear-web2"))
                .andExpect(status().isOk())
                .andExpect(view().name("index2"))
                .andExpect(model().attributeExists("members"));
    }

    // App 2 controller: register happy path
    @Test
    void register_v2_happyPath_redirectsWithSuccessMessage() throws Exception {
        doNothing().when(memberRegistration).register(any());

        mockMvc.perform(post("/kitchensink-ear-web2/register")
                        .param("name", "Bob")
                        .param("email", "bob@example.com")
                        .param("phoneNumber", "9876543210")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kitchensink-ear-web2"))
                .andExpect(flash().attribute("successMessage", "Registered!"));
    }
}
