package com.example.kitchensink.api;

import com.example.kitchensink.domain.Member;
import com.example.kitchensink.repository.MemberRepository;
import com.example.kitchensink.service.MemberRegistrationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/rest")
public class MemberRestController {

    private static final Logger log = LoggerFactory.getLogger(MemberRestController.class);

    private final MemberRepository memberRepository;
    private final MemberRegistrationService memberRegistrationService;

    public MemberRestController(MemberRepository memberRepository, MemberRegistrationService memberRegistrationService) {
        this.memberRepository = memberRepository;
        this.memberRegistrationService = memberRegistrationService;
    }

    @GetMapping("/members")
    public List<Member> listAllMembers() {
        log.info("INFO:  Requesting all users....");
        return memberRepository.findAllByOrderByNameAsc();
    }

    @GetMapping("/members/{id}")
    public ResponseEntity<Member> lookupMemberById(@PathVariable long id) {
        return memberRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/members")
    public ResponseEntity<Map<String, String>> createMember(@Valid @RequestBody Member member) {
        if (memberRepository.findByEmail(member.getEmail()).isPresent()) {
            Map<String, String> responseObj = new HashMap<>();
            responseObj.put("email", "Email taken");
            return ResponseEntity.status(409).body(responseObj);
        }
        try {
            memberRegistrationService.register(member);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            Map<String, String> responseObj = new HashMap<>();
            responseObj.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(responseObj);
        }
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationErrors(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.put(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest().body(errors);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericError(Exception ex) {
        Map<String, String> responseObj = new HashMap<>();
        responseObj.put("error", ex.getMessage());
        return ResponseEntity.badRequest().body(responseObj);
    }
}
