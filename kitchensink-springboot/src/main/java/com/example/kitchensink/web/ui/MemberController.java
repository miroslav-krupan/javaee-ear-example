package com.example.kitchensink.web.ui;

import com.example.kitchensink.domain.Member;
import com.example.kitchensink.repository.MemberRepository;
import com.example.kitchensink.service.MemberRegistration;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class MemberController {

    private static final Logger log = LoggerFactory.getLogger(MemberController.class);

    private final MemberRegistration memberRegistration;
    private final MemberRepository memberRepository;

    @Value("${config.key:Default value}")
    private String configValue;

    public MemberController(MemberRegistration memberRegistration, MemberRepository memberRepository) {
        this.memberRegistration = memberRegistration;
        this.memberRepository = memberRepository;
    }

    @GetMapping("/")
    public String showRegistrationForm(Model model) {
        if (!model.containsAttribute("newMember")) {
            model.addAttribute("newMember", new Member());
        }
        model.addAttribute("members", memberRepository.findAllByOrderByNameAsc());
        log.info("Config value: {}", configValue);
        return "index";
    }

    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute("newMember") Member member,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttrs) {
        if (!result.hasErrors() && memberRepository.findByEmail(member.getEmail()).isPresent()) {
            result.rejectValue("email", "duplicate", "Email taken");
        }
        if (result.hasErrors()) {
            model.addAttribute("members", memberRepository.findAllByOrderByNameAsc());
            return "index";
        }
        try {
            memberRegistration.register(member);
            redirectAttrs.addFlashAttribute("successMessage", "Registered!");
            return "redirect:/";
        } catch (Exception e) {
            model.addAttribute("errorMessage", getRootErrorMessage(e));
            model.addAttribute("members", memberRepository.findAllByOrderByNameAsc());
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
