# Validation Report — loop-1

**Generated:** 2026-09-01T09:55Z  
**Result:** ❌ FAIL — 2 open MAJOR findings block promotion

---

## Validator Summary

| Validator | Status | Findings |
|---|---|---|
| Build & Startup | ✅ PASS | 0 findings |
| Security | ✅ PASS | 0 findings |
| Architecture | ❌ FAIL | 2 major, 1 minor |
| Test & Behavioral | ⚠️ WARN | 0 major, 6 minor |

---

## Open Findings — MAJOR (must fix)

### `bb44a7a4f887` — required-artifact-missing
- **Location:** `kitchensink/src/test/java/org/example/kitchensink/model/MemberValidationTest.java`
- **Validator:** architecture
- **Summary:** MemberValidationTest.java listed in architecture §1.2 target layout is absent; only a .gitkeep placeholder exists.

### `4ba5535eb380` — arch-drift
- **Location:** `CommonLibsEar.zip`
- **Validator:** architecture
- **Summary:** Architecture §9 mandates deletion of CommonLibsEar.zip; the file (1 MB) remains in the repository root.

---

## Open Findings — MINOR (non-blocking)

### `c95d9b1fb2ea` — spring-idiom
- **Location:** `kitchensink/src/test/java/org/example/kitchensink/repository/MemberRepositoryTest.java`
- **Validator:** architecture
- **Summary:** MemberRepositoryTest imports `jakarta.transaction.Transactional` instead of `org.springframework.transaction.annotation.Transactional`; Spring test rollback semantics require the Spring annotation.

### `f30c3d0dd776` — coverage-gap
- **Location:** gap-2
- **Validator:** test-behavioral
- **Summary:** No test verifies that Member.name with digits fails the @Pattern constraint.

### `2bc482c16f48` — coverage-gap
- **Location:** gap-4
- **Validator:** test-behavioral
- **Summary:** No test verifies that Member.name longer than 25 characters fails @Size(max=25).

### `c044dc8e072b` — coverage-gap
- **Location:** gap-6
- **Validator:** test-behavioral
- **Summary:** No test verifies that a blank/empty email fails @NotBlank (migrated from @NotEmpty).

### `228bf02c5eb5` — coverage-gap
- **Location:** gap-8
- **Validator:** test-behavioral
- **Summary:** No test verifies that Member.phoneNumber longer than 12 digits fails @Size(max=12).

### `0f1dc32a0e3a` — coverage-gap
- **Location:** gap-9
- **Validator:** test-behavioral
- **Summary:** No test verifies that Member.phoneNumber with non-digit characters fails @Digits.

### `504ce1011be8` — coverage-gap
- **Location:** gap-12
- **Validator:** test-behavioral
- **Summary:** No test verifies that MemberRepository.findById() returns empty/null for an unknown id.

---

## Parked Known-Issues

None.

---

## Next Action

Emitting `validation-failed` → Migration agent will perform targeted rework of the 2 MAJOR findings. Loop will continue as loop-2.
