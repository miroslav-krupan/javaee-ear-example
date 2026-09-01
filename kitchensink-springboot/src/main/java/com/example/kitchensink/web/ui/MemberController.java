package com.example.kitchensink.web.ui;

import com.example.kitchensink.model.Member;
import com.example.kitchensink.repository.MemberRepository;
import com.example.kitchensink.service.MemberRegistration;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Migrated from legacy JSF @Model MemberController (web module).
 * Context-root /kitchensink-ear-web preserved as request-mapping prefix.
 * MemberListProducer merged here — each GET fetches the current members list
 * from the repository (request-scoped refresh semantics preserved).
 * Gson dead-code block from legacy initNewMember() is deleted per architecture decision.
 */
@Controller
@RequestMapping("/kitchensink-ear-web")
public class MemberController {

    private final MemberRegistration memberRegistration;
    private final MemberRepository memberRepository;

    public MemberController(MemberRegistration memberRegistration, MemberRepository memberRepository) {
        this.memberRegistration = memberRegistration;
        this.memberRepository = memberRepository;
    }

    @GetMapping
    public String showForm(Model model) {
        if (!model.containsAttribute("newMember")) {
            model.addAttribute("newMember", new Member());
        }
        model.addAttribute("members", memberRepository.findAllOrderedByName());
        return "index";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("newMember") Member member,
                           BindingResult bindingResult,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("members", memberRepository.findAllOrderedByName());
            return "index";
        }
        try {
            memberRegistration.register(member);
            redirectAttributes.addFlashAttribute("successMessage", "Registered!");
            return "redirect:/kitchensink-ear-web";
        } catch (Exception e) {
            model.addAttribute("errorMessage", getRootErrorMessage(e));
            model.addAttribute("members", memberRepository.findAllOrderedByName());
            return "index";
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
