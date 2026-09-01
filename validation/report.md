# Validation Report — loop-3 (FINAL)

**Generated:** 2026-09-01T10:11Z  
**Result:** ⚠️ GLOBAL CAP REACHED — 3 loops completed without resolving all findings. PR marked ready-for-review; all remaining findings documented as known-issues for human review.

---

## Validator Summary

| Validator | Loop-3 Status | Notes |
|---|---|---|
| Build & Startup | ✅ PASS | 0 findings across all loops |
| Security | ✅ PASS | 0 findings across all loops |
| Architecture | ⚠️ unchanged | 3 findings open across loop-1/2/3 — now parked |
| Test & Behavioral | ⚠️ unchanged | 6 findings open across loop-1/2/3 — now parked |

---

## Parked Known-Issues (chronic — open across loop-1, loop-2, loop-3)

These items were not resolved by Migration across 3 rework cycles. They require **human attention** before or after ship.

### MAJOR — require human sign-off before merging to main

#### `bb44a7a4f887` — required-artifact-missing
- **Location:** `kitchensink/src/test/java/org/example/kitchensink/model/MemberValidationTest.java`
- **Validator:** architecture
- **Summary:** MemberValidationTest.java listed in architecture §1.2 target layout is absent; only a `.gitkeep` placeholder exists.

#### `4ba5535eb380` — arch-drift
- **Location:** `CommonLibsEar.zip`
- **Validator:** architecture
- **Summary:** `CommonLibsEar.zip` (1 MB) remains in the repository root. Architecture §9 mandates its deletion.

### MINOR — advisory, can be addressed post-merge

#### `c95d9b1fb2ea` — spring-idiom
- **Location:** `kitchensink/src/test/java/org/example/kitchensink/repository/MemberRepositoryTest.java`
- **Summary:** MemberRepositoryTest uses `jakarta.transaction.Transactional` instead of `org.springframework.transaction.annotation.Transactional`.

#### Coverage-gap findings (×6) — test-behavioral
| findingId | Summary |
|---|---|
| `f30c3d0dd776` | No test: Member.name with digits fails @Pattern |
| `2bc482c16f48` | No test: Member.name > 25 chars fails @Size(max=25) |
| `c044dc8e072b` | No test: blank email fails @NotBlank |
| `228bf02c5eb5` | No test: Member.phoneNumber > 12 digits fails @Size(max=12) |
| `0f1dc32a0e3a` | No test: non-digit phoneNumber fails @Digits |
| `504ce1011be8` | No test: MemberRepository.findById() returns empty for unknown id |

---

## What passed cleanly

- Maven build compiles and application starts successfully
- Security posture is clean (no `weblogic.*` / `javax.*` remnants, no credential leaks)
- Core business logic, persistence, sync/async communication all migrated
- Thymeleaf frontend migration complete
