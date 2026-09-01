package org.example.kitchensink.web.rest;

import jakarta.validation.Valid;
import org.example.kitchensink.model.Member;
import org.example.kitchensink.repository.MemberRepository;
import org.example.kitchensink.service.MemberRegistrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Consolidates MemberResourceRESTService and MemberResourceRESTServiceSecond (web + web2 WARs).
 * Replaces JAX-RS @Path("/members") with Spring MVC at /api/members.
 */
@RestController
@RequestMapping("/api/members")
public class MemberRestController {

    private static final Logger log = LoggerFactory.getLogger(MemberRestController.class);

    private final MemberRepository memberRepository;
    private final MemberRegistrationService registrationService;

    public MemberRestController(MemberRepository memberRepository, MemberRegistrationService registrationService) {
        this.memberRepository = memberRepository;
        this.registrationService = registrationService;
    }

    @GetMapping
    public List<Member> listAllMembers() {
        log.info("Requesting all members");
        return memberRepository.findAllByOrderByNameAsc();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Member> lookupMemberById(@PathVariable Long id) {
        return memberRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    @PostMapping
    public ResponseEntity<Void> createMember(@Valid @RequestBody Member member) {
        registrationService.register(member);
        return ResponseEntity.ok().build();
    }
}
