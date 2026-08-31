package com.example.kitchensink.web.primary;

import com.example.kitchensink.model.Member;
import com.example.kitchensink.repository.MemberRepository;
import com.example.kitchensink.service.MemberRegistration;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import jakarta.validation.Validator;
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Source: web/src/main/java/.../rest/MemberResourceRESTService.java
// JAX-RS @Path/@GET/@POST/@PathParam → Spring @RestController/@GetMapping/@PostMapping/@PathVariable;
// javax.ws.rs.Response → ResponseEntity; javax.validation.* → jakarta.validation.*;
// @Inject → constructor injection; repository.findById returns Optional (JpaRepository contract);
// emailAlreadyExists uses findByEmailOptional to avoid NoResultException coupling.
@RestController
@RequestMapping("/web/rest")
public class MemberResourceRESTService {

    private static final Logger logger = LoggerFactory.getLogger(MemberResourceRESTService.class);

    private final Validator validator;
    private final MemberRepository repository;
    private final MemberRegistration registration;

    public MemberResourceRESTService(Validator validator, MemberRepository repository, MemberRegistration registration) {
        this.validator = validator;
        this.repository = repository;
        this.registration = registration;
    }

    @GetMapping(value = "/members", produces = "application/json")
    public List<Member> listAllMembers() {
        logger.info("INFO:  Requesting all users....");
        return repository.findAllOrderedByName();
    }

    @GetMapping(value = "/members/{id:[0-9]+}", produces = "application/json")
    public ResponseEntity<Member> lookupMemberById(@PathVariable long id) {
        return repository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/members", consumes = "application/json", produces = "application/json")
    public ResponseEntity<?> createMember(@RequestBody Member member) {
        try {
            validateMember(member);
            registration.register(member);
            return ResponseEntity.ok().build();
        } catch (ConstraintViolationException ce) {
            return createViolationResponse(ce.getConstraintViolations());
        } catch (ValidationException e) {
            Map<String, String> responseObj = new HashMap<>();
            responseObj.put("email", "Email taken");
            return ResponseEntity.status(HttpStatus.CONFLICT).body(responseObj);
        } catch (Exception e) {
            Map<String, String> responseObj = new HashMap<>();
            responseObj.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(responseObj);
        }
    }

    private void validateMember(Member member) throws ConstraintViolationException, ValidationException {
        Set<ConstraintViolation<Member>> violations = validator.validate(member);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(new HashSet<>(violations));
        }
        if (emailAlreadyExists(member.getEmail())) {
            throw new ValidationException("Unique Email Violation");
        }
    }

    private ResponseEntity<Map<String, String>> createViolationResponse(Set<ConstraintViolation<?>> violations) {
        logger.debug("Validation completed. violations found: " + violations.size());
        Map<String, String> responseObj = new HashMap<>();
        for (ConstraintViolation<?> violation : violations) {
            responseObj.put(violation.getPropertyPath().toString(), violation.getMessage());
        }
        return ResponseEntity.badRequest().body(responseObj);
    }

    public boolean emailAlreadyExists(String email) {
        return repository.findByEmailOptional(email).isPresent();
    }
}
