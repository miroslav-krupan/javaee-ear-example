package com.example.kitchensink.web.primary;

import com.example.kitchensink.model.Member;
import com.example.kitchensink.repository.MemberRepository;
import com.example.kitchensink.service.MemberRegistration;
import com.google.gson.Gson;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// Source: web/src/main/java/.../controller/MemberController.java
// @Model (@Named @RequestScoped) -> @Controller @RequestScope;
// FacesContext.addMessage -> RedirectAttributes flash / Model error attribute;
// Gson debug log in initNewMember preserved as-is (target-architecture §6).
@Controller
@RequestScope
public class MemberController {

    private static final Logger log = LoggerFactory.getLogger(MemberController.class);

    private final MemberRegistration memberRegistration;
    private final MemberRepository memberRepository;

    private Member newMember;

    public MemberController(MemberRegistration memberRegistration, MemberRepository memberRepository) {
        this.memberRegistration = memberRegistration;
        this.memberRepository = memberRepository;
    }

    @ModelAttribute("newMember")
    public Member getNewMember() {
        return newMember;
    }

    @PostConstruct
    public void initNewMember() {
        newMember = new Member();

        Member testMember = new Member();
        testMember.setEmail("test@mail.gr");
        Gson g = new Gson();
        String s = g.toJson(testMember);

        log.info("GSON TEST MEMBER = " + s);
    }

    @GetMapping("/web")
    public String index(Model model) {
        model.addAttribute("members", memberRepository.findAllOrderedByName());
        return "web/index";
    }

    @PostMapping("/web/register")
    public String register(@Valid @ModelAttribute("newMember") Member member,
                           BindingResult result,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("members", memberRepository.findAllOrderedByName());
            return "web/index";
        }
        try {
            memberRegistration.register(member);
            redirectAttributes.addFlashAttribute("successMessage", "Registered!");
            return "redirect:/web";
        } catch (Exception e) {
            model.addAttribute("errorMessage", getRootErrorMessage(e));
            model.addAttribute("members", memberRepository.findAllOrderedByName());
            return "web/index";
        }
    }

    private String getRootErrorMessage(Exception e) {
        String errorMessage = "Registration failed. See server log for more information";
        if (e == null) {
            return errorMessage;
        }
        Throwable t = e;
        while (t != null) {
            errorMessage = t.getLocalizedMessage();
            t = t.getCause();
        }
        return errorMessage;
    }
}
