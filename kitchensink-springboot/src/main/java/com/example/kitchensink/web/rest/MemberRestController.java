package com.example.kitchensink.web.rest;

import com.example.kitchensink.domain.Member;
import com.example.kitchensink.repository.MemberRepository;
import com.example.kitchensink.service.MemberRegistration;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/rest/members")
public class MemberRestController {

    private static final Logger log = LoggerFactory.getLogger(MemberRestController.class);

    private final Validator validator;
    private final MemberRepository memberRepository;
    private final MemberRegistration registration;

    public MemberRestController(Validator validator, MemberRepository memberRepository, MemberRegistration registration) {
        this.validator = validator;
        this.memberRepository = memberRepository;
        this.registration = registration;
    }

    @GetMapping(produces = "application/json")
    public List<Member> listAllMembers() {
        log.info("Requesting all members");
        return memberRepository.findAllByOrderByNameAsc();
    }

    @GetMapping(value = "/{id:\\d+}", produces = "application/json")
    public ResponseEntity<Member> lookupMemberById(@PathVariable long id) {
        return memberRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<Map<String, String>> createMember(@RequestBody Member member) {
        try {
            validateMember(member);
            registration.register(member);
            return ResponseEntity.ok().build();
        } catch (ConstraintViolationException ce) {
            return ResponseEntity.badRequest().body(violationMap(ce.getConstraintViolations()));
        } catch (ValidationException e) {
            Map<String, String> body = new HashMap<>();
            body.put("email", "Email taken");
            return ResponseEntity.status(409).body(body);
        } catch (Exception e) {
            Map<String, String> body = new HashMap<>();
            body.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(body);
        }
    }

    private void validateMember(Member member) {
        Set<ConstraintViolation<Member>> violations = validator.validate(member);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(new HashSet<>(violations));
        }
        if (emailAlreadyExists(member.getEmail())) {
            throw new ValidationException("Unique Email Violation");
        }
    }

    private Map<String, String> violationMap(Set<ConstraintViolation<?>> violations) {
        log.debug("Validation completed. violations found: {}", violations.size());
        Map<String, String> map = new HashMap<>();
        for (ConstraintViolation<?> v : violations) {
            map.put(v.getPropertyPath().toString(), v.getMessage());
        }
        return map;
    }

    boolean emailAlreadyExists(String email) {
        return memberRepository.findByEmail(email).isPresent();
    }
}
