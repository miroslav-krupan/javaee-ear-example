package com.example.kitchensink.web;

import com.example.kitchensink.domain.Member;
import com.example.kitchensink.service.MemberRegistrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/")
public class MemberController {

    private static final Logger log = LoggerFactory.getLogger(MemberController.class);

    private final String configKey;
    private final MemberRegistrationService memberRegistrationService;
    private final MemberListModel memberListModel;

    public MemberController(@Value("${config.key:Default value}") String configKey,
                            MemberRegistrationService memberRegistrationService,
                            MemberListModel memberListModel) {
        this.configKey = configKey;
        this.memberRegistrationService = memberRegistrationService;
        this.memberListModel = memberListModel;
    }

    @GetMapping
    public String index(Model model) {
        log.info("DELTASPIKE Config Value = [{}]", configKey);
        model.addAttribute("members", memberListModel.getMembers());
        model.addAttribute("newMember", new Member());
        return "index";
    }

    @PostMapping("/members/register")
    public String register(@ModelAttribute("newMember") Member member, RedirectAttributes redirectAttributes) {
        try {
            memberRegistrationService.register(member);
            redirectAttributes.addFlashAttribute("successMessage", "Registered!");
        } catch (Exception e) {
            String errorMessage = getRootErrorMessage(e);
            redirectAttributes.addFlashAttribute("errorMessage", errorMessage);
        }
        return "redirect:/";
    }

    String getRootErrorMessage(Exception e) {
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
