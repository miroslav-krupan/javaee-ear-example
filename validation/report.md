# Validation Report — loop-1

**Date:** 2026-09-01  
**Issue:** miroslav-krupan/javaee-ear-example#9  
**Loop:** loop-1  
**Gate result:** ❌ FAIL — 1 open MAJOR finding

---

## Validator Summary

| Validator | Result | Findings |
|---|---|---|
| Build & Startup | ✅ PASS | 0 findings |
| Test & Behavioral | ✅ PASS (32/32 green) | 1 minor |
| Architecture | ✅ PASS | 1 minor |
| Security | ❌ FAIL | 1 **major** |

---

## OPEN Findings

### Major (blocking)

| findingId | Validator | Rule | Location | Summary |
|---|---|---|---|---|
| `23799dd64148` | security | auth-downgrade | `kitchensink-springboot/src/main/resources/application.properties` | H2 web console enabled (`spring.h2.console.enabled=true`) — unauthenticated DB access endpoint introduced; original app had no such endpoint |

### Minor (non-blocking)

| findingId | Validator | Rule | Location | Summary |
|---|---|---|---|---|
| `f82eaf263d3d` | test-behavioral | coverage-gap | `gap-25-MemberListProducer-observer` | Gap 25 not covered: MemberListProducer @Observes Member observer pattern has no dedicated test |
| `70c264759b7a` | architecture | spring-idiom | `kitchensink-springboot/src/main/java/com/example/kitchensink/web/ui/MemberController.java` | MemberController injects @Value via field injection; preferred Spring idiom is constructor injection |

---

## Tests

32 tests — all green. Full test story:

<!-- test-report-embed -->
### Test Summary (from Test & Behavioral Validation)

**32/32 tests passing** — 1 migrated test + 31 new gap-coverage tests.

| Test class | Tests | Pass |
|---|---|---|
| `KitchensinkApplicationContextTest` | 1 | ✅ |
| `MemberValidationTest` | 12 | ✅ |
| `MemberRepositoryTest` | 5 | ✅ |
| `MemberRegistrationTest` | 1 | ✅ |
| `MemberRestControllerTest` | 8 | ✅ |
| `MemberControllerTest` | 5 | ✅ |
| **TOTAL** | **32** | **✅** |

**1 gap still without a test:** Gap 25 — MemberListProducer observer (minor finding `f82eaf263d3d`).

See full test detail in [`validation/test-report.md`](test-report.md).

---

## Parked Findings

None — no finding has been open across ≥3 loops yet.

---

## Decision

**FAIL.** Emitting `validation-failed` to route the 1 major finding back to Migration for targeted rework:

- **`23799dd64148`** — Remove `spring.h2.console.enabled=true` from `application.properties` (move to `application-dev.properties` or delete entirely). This is a security regression vs. the original app.

Loop will advance to **loop-2** after rework.
