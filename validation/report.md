# Validation Report — loop-1

**Date:** 2026-08-31  
**Coordinator:** private-demo-springboot-migration-validation  
**Decision:** ❌ FAIL — open findings require rework before loop-2

---

## Per-Validator Summary

| Validator | Result | Findings |
|---|---|---|
| Build & Startup | ✅ PASS | 0 (booted on H2 in-memory) |
| Security | ⚠️ WARN (non-blocking) | 2 warns (legacy source), 1 info |
| Test & Behavioral | ❌ FAIL | 8 missing test cases |
| Architecture | ❌ FAIL | 6 open drift items (1 critical) |

---

## Open Findings (blocking — require rework)

### Architecture (6)

| ID | Rule | Summary |
|---|---|---|
| ARCH-001 | cited-file-missing | `config/WebConfig.java` not present (target §1) |
| ARCH-002 | cited-file-missing | `web/primary/MemberController.java` (Thymeleaf UI) not present (target §1, §6) |
| ARCH-003 | cited-file-missing | `web/secondary/MemberControllerSecond.java` + `@Value` config not present (target §1, §6, §7) |
| ARCH-004 | arch-drift | `data/MemberListProducer.java` present — target §4 says eliminate it |
| **ARCH-005** | **spring-idiom** | **CRITICAL: H2 `scope=test` in pom.xml but application.properties configures H2 datasource — fat jar will not boot** |
| ARCH-006 | no-x-present | No `@DataJpaTest` for `MemberRepository` — required by target §14 |

### Test & Behavioral (8)

| ID | Rule | Summary |
|---|---|---|
| tb-001 | test-completeness | No test: Member name with digits fails `@Pattern([^0-9]*)` |
| tb-002 | test-completeness | No test: Member name length boundaries (empty / 25-char OK / 26-char fail) |
| tb-003 | test-completeness | No test: Member email invalid format fails `@Email` |
| tb-004 | test-completeness | No test: Member phone length boundaries (9-char fail / 10–12-char OK / 13-char fail) |
| tb-005 | test-completeness | No test: Member phone non-digits fails `@Digits` |
| tb-006 | test-completeness | No test: `MemberRepository.findByEmail` not-found path |
| tb-007 | test-completeness | No test: `MemberRepository.findById` not-found path |
| tb-008 | test-completeness | No test: `MemberRepository` `NonUniqueResultException` propagation |

---

## Non-Blocking Findings (known issues, do not block)

| ID | Validator | Status | Summary |
|---|---|---|---|
| sec-001 | security | warn | Hardcoded WebLogic password in legacy `ear/pom.xml` — not in migrated module |
| sec-002 | security | warn | Log4j 1.x in legacy `ear/pom.xml` — not carried forward to Spring Boot module |
| sec-003 | security | pass | No auth downgrade — original app had no security constraints |
| sec-004 | security | info | H2 dev/test datasource uses empty password — acceptable for in-memory test scope |

---

## Parked Known Issues

None.

---

## Next Loop: loop-2

Migration rework required. Priority order:
1. **ARCH-005 (critical):** Fix H2 scope (`test` → `runtime`) or replace datasource config
2. **ARCH-002/003:** Implement `MemberController.java` and `MemberControllerSecond.java` (Thymeleaf UI)
3. **ARCH-001:** Add `config/WebConfig.java`
4. **ARCH-004:** Remove `data/MemberListProducer.java` (target §4 says eliminate)
5. **ARCH-006:** Add `@DataJpaTest` for `MemberRepository`
6. **tb-001 to tb-008:** Add 8 missing boundary/edge-case tests
