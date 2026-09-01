package org.example.kitchensink.web.ui;

import jakarta.validation.Valid;
import org.example.kitchensink.model.Member;
import org.example.kitchensink.repository.MemberRepository;
import org.example.kitchensink.service.EmailAlreadyExistsException;
import org.example.kitchensink.service.MemberRegistrationService;
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

@Controller
@RequestMapping("/members")
public class MemberController {

    private static final Logger log = LoggerFactory.getLogger(MemberController.class);

    private final MemberRepository memberRepository;
    private final MemberRegistrationService memberRegistrationService;
    private final String configKey;

    public MemberController(MemberRepository memberRepository,
                            MemberRegistrationService memberRegistrationService,
                            @Value("${config.key:Default value}") String configKey) {
        this.memberRepository = memberRepository;
        this.memberRegistrationService = memberRegistrationService;
        this.configKey = configKey;
    }

    @GetMapping
    public String showForm(Model model) {
        if (!model.containsAttribute("newMember")) {
            model.addAttribute("newMember", new Member());
        }
        model.addAttribute("members", memberRepository.findAllByOrderByNameAsc());
        log.debug("Config key: {}", configKey);
        return "members/index";
    }

    @PostMapping
    public String register(@Valid @ModelAttribute("newMember") Member newMember,
                           BindingResult bindingResult,
                           RedirectAttributes redirectAttributes,
                           Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("members", memberRepository.findAllByOrderByNameAsc());
            return "members/index";
        }
        try {
            memberRegistrationService.register(newMember);
            redirectAttributes.addFlashAttribute("successMessage", "Registered!");
        } catch (EmailAlreadyExistsException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Email already registered: " + newMember.getEmail());
            redirectAttributes.addFlashAttribute("newMember", newMember);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", getRootErrorMessage(e));
            redirectAttributes.addFlashAttribute("newMember", newMember);
        }
        return "redirect:/members";
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
