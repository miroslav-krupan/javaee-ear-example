package com.example.kitchensink.web.ui;

import com.example.kitchensink.model.Member;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Migrated from legacy JSF @Model MemberControllerSecond (web2 module).
 * Context-root /kitchensink-ear-web2 preserved as request-mapping prefix.
 * DeltaSpike ConfigResolver.getPropertyValue("config.key") replaced with
 * Spring @Value constructor injection per architecture decision.
 */
@Controller
@RequestMapping("/kitchensink-ear-web2")
public class MemberControllerV2 {

    private static final Logger logger = LoggerFactory.getLogger(MemberControllerV2.class);

    private final String configKey;
    private final MemberRegistration memberRegistration;
    private final MemberRepository memberRepository;

    public MemberControllerV2(@Value("${config.key:Default value}") String configKey,
                               MemberRegistration memberRegistration,
                               MemberRepository memberRepository) {
        this.configKey = configKey;
        this.memberRegistration = memberRegistration;
        this.memberRepository = memberRepository;
        logger.info("DeltaSpike Config Value = [{}]", configKey);
    }

    @GetMapping
    public String showForm(Model model) {
        if (!model.containsAttribute("newMember")) {
            model.addAttribute("newMember", new Member());
        }
        model.addAttribute("members", memberRepository.findAllOrderedByName());
        return "index2";
    }

    @PostMapping("/register")
    public String register(@Valid @ModelAttribute("newMember") Member member,
                           BindingResult bindingResult,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("members", memberRepository.findAllOrderedByName());
            return "index2";
        }
        try {
            memberRegistration.register(member);
            redirectAttributes.addFlashAttribute("successMessage", "Registered!");
            return "redirect:/kitchensink-ear-web2";
        } catch (Exception e) {
            model.addAttribute("errorMessage", getRootErrorMessage(e));
            model.addAttribute("members", memberRepository.findAllOrderedByName());
            return "index2";
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
