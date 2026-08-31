# Validation Report — loop-2

**Date:** 2026-08-31  
**Coordinator:** private-demo-springboot-migration-validation  
**Decision:** ❌ FAIL — 5 open findings require rework before loop-3

---

## Per-Validator Summary

| Validator | Result | New Findings |
|---|---|---|
| Build & Startup | ❌ FAIL | 1 open (mvn verify fails — test exception mismatch) |
| Security | ✅ PASS | 3 pass (no new issues in migrated module) |
| Test & Behavioral | ❌ FAIL | 1 open (wrong exception type in repo test) |
| Architecture | ❌ FAIL | 3 still open (WebConfig, MemberListProducer, H2 scope) |

---

## Resolved Since loop-1 ✅

| ID | What was fixed |
|---|---|
| ARCH-002 | `web/primary/MemberController.java` added (Thymeleaf @Controller) |
| ARCH-003 | `web/secondary/MemberControllerSecond.java` added with `@Value` config |
| ARCH-006 | `MemberRepositoryTest` with `@DataJpaTest` added |
| tb-001–008 | All 8 test coverage gaps now covered by `MemberValidationTest` + `MemberRepositoryTest` |
| sec-001 | Legacy `ear/pom.xml` WebLogic password — not re-raised (out of migration scope) |
| sec-002 | Legacy `ear/pom.xml` Log4j 1.x — not re-raised (not carried forward) |

---

## Open Findings (blocking — require rework)

### Build & Startup (1)

| ID | Rule | Summary |
|---|---|---|
| c59c9b7b12da | build | `mvn verify` FAIL — `MemberRepositoryTest.findByEmail_notFound_throwsNoResultException` expects `jakarta.persistence.NoResultException` but receives `org.springframework.dao.EmptyResultDataAccessException` (Spring @Repository wraps it) |

### Test & Behavioral (1)

| ID | Rule | Summary |
|---|---|---|
| tb-l2-001 | test-failure | Same as above — test asserts wrong exception type; must catch `EmptyResultDataAccessException` or change assertion to `assertThat(result).isEmpty()` pattern |

### Architecture (3)

| ID | Rule | Summary |
|---|---|---|
| ARCH-001-L2 | cited-file-missing | `config/WebConfig.java` still missing; context paths `/web`, `/web2` hardcoded in controller annotations rather than configurable via `application.properties` |
| ARCH-004-L2 | arch-drift | `data/MemberListProducer.java` still present — target §4 says eliminate; `data/` package not in target layout |
| **ARCH-005-L2** | **spring-idiom** | **CRITICAL: H2 `scope=test` in `pom.xml` but `application.properties` configures H2 datasource — packaged fat jar will not boot** |

---

## Non-Blocking (known issues, do not block)

| ID | Validator | Status | Summary |
|---|---|---|---|
| sec-004 | security | info | H2 dev/test datasource empty password — acceptable for in-memory scope |

---

## Parked Known Issues

None (no finding has appeared open in ≥3 distinct loopIds yet).

---

## Next Loop: loop-3

Priority order for migration rework:
1. **ARCH-005 (critical):** Fix H2 scope: change `<scope>test</scope>` → `<scope>runtime</scope>` in `kitchensink/pom.xml`, or replace datasource config with a proper runtime DB
2. **tb-l2-001 / c59c9b7b12da:** Fix `MemberRepositoryTest.findByEmail_notFound` — assert `EmptyResultDataAccessException` (or use `Optional` pattern and assert empty)
3. **ARCH-001:** Add `config/WebConfig.java` with configurable context paths, or move path values to `application.properties` with `@Value` injection
4. **ARCH-004:** Delete `data/MemberListProducer.java` and its `data/` package; ensure controllers call service directly
