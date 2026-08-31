package com.example.kitchensink.web.secondary;

import com.example.kitchensink.model.Member;
import com.example.kitchensink.repository.MemberRepository;
import com.example.kitchensink.service.MemberRegistration;
import jakarta.annotation.PostConstruct;
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
import org.springframework.web.context.annotation.RequestScope;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

// Source: web2/src/main/java/.../controller/MemberControllerSecond.java
// @Model (@Named @RequestScoped) -> @Controller @RequestScope;
// DeltaSpike ConfigResolver.getPropertyValue("config.key","Default value") -> @Value (target-architecture §6/§7);
// FacesContext.addMessage -> RedirectAttributes flash / Model error attribute.
@Controller
@RequestScope
public class MemberControllerSecond {

    private static final Logger logger = LoggerFactory.getLogger(MemberControllerSecond.class);

    private final MemberRegistration memberRegistration;
    private final MemberRepository memberRepository;

    @Value("${config.key:Default value}")
    private String configValue;

    private Member newMember;

    public MemberControllerSecond(MemberRegistration memberRegistration, MemberRepository memberRepository) {
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

        logger.info("DELTASPIKE Config Value = [{}]", configValue);
    }

    @GetMapping("/web2")
    public String index(Model model) {
        model.addAttribute("members", memberRepository.findAllOrderedByName());
        return "web2/index";
    }

    @PostMapping("/web2/register")
    public String register(@Valid @ModelAttribute("newMember") Member member,
                           BindingResult result,
                           Model model,
                           RedirectAttributes redirectAttributes) {
        if (result.hasErrors()) {
            model.addAttribute("members", memberRepository.findAllOrderedByName());
            return "web2/index";
        }
        try {
            memberRegistration.register(member);
            redirectAttributes.addFlashAttribute("successMessage", "Registered!");
            return "redirect:/web2";
        } catch (Exception e) {
            model.addAttribute("errorMessage", getRootErrorMessage(e));
            model.addAttribute("members", memberRepository.findAllOrderedByName());
            return "web2/index";
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
