package com.example.kitchensink.web.rest;

import com.example.kitchensink.model.Member;
import com.example.kitchensink.repository.MemberRepository;
import com.example.kitchensink.service.EmailAlreadyExistsException;
import com.example.kitchensink.service.MemberRegistration;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Migrated from JAX-RS MemberResourceRESTServiceSecond (web2 module, @Path("/members")).
 * Context root /kitchensink-ear-web2 collapses into Spring Boot; mapped at /rest2/members.
 * Identical behaviour to MemberRestController at a different path prefix.
 */
@RestController
@RequestMapping("/rest2/members")
public class MemberRestControllerV2 {

    private final MemberRepository memberRepository;
    private final MemberRegistration memberRegistration;

    public MemberRestControllerV2(MemberRepository memberRepository, MemberRegistration memberRegistration) {
        this.memberRepository = memberRepository;
        this.memberRegistration = memberRegistration;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Member> listAllMembers() {
        return memberRepository.findAllOrderedByName();
    }

    @GetMapping(value = "/{id:[0-9][0-9]*}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Member> lookupMemberById(@PathVariable long id) {
        Member member = memberRepository.findById(id);
        if (member == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(member);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createMember(@Valid @RequestBody Member member) {
        try {
            memberRegistration.register(member);
            return ResponseEntity.ok().build();
        } catch (EmailAlreadyExistsException e) {
            Map<String, String> response = new HashMap<>();
            response.put("email", "Email taken");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
        } catch (Exception e) {
            Map<String, String> response = new HashMap<>();
            response.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}
