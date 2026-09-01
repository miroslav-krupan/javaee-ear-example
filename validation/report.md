# Validation Report — loop-1

**Date:** 2026-09-01  
**Result:** REWORK REQUIRED — 5 open findings

---

## Validator Summary

| Validator | Result | Open Findings |
|---|---|---|
| Build & Startup | **FAIL** | 3 |
| Test & Behavioral | **PASS** | 0 |
| Architecture | **FAIL** | 2 |
| Security | **PASS** | 0 |

---

## Open Findings

### Build & Startup

**[850d5fb9] mvn-verify — build**  
`mvn -q verify` exits 1: 5 test errors from two root causes — request-scope proxy missing (MemberRegistrationServiceTest) and Flyway/Hibernate sequence mismatch (MemberRepositoryTest x4).

**[200a3c35] request-scope-proxy — startup**  
`MemberListModel` is `@RequestScope` but injected into singleton `MemberController` without a scoped proxy; `@SpringBootTest` context load fails with `ScopeNotActiveException`.  
*Fix:* add `@Scope(value = "request", proxyMode = ScopedProxyMode.TARGET_CLASS)` to `MemberListModel`.

**[612a6209] schema-validation — startup**  
V1 Flyway migration uses `IDENTITY` column, not a sequence; Hibernate 6 `@GeneratedValue` (default `SEQUENCE`) expects `aa_registrant_seq`; `ddl-auto=validate` causes `@DataJpaTest` context to fail with `SchemaManagementException`.  
*Fix:* add a V2 migration creating the sequence, or align `@GeneratedValue` strategy to `IDENTITY`.

### Architecture

**[arch-loop1-001] cited-file-missing — arch-drift**  
`logback-spring.xml` absent from `src/main/resources/` — target-architecture §6 prescribes it as the mandatory replacement for the old `log4j.xml` in each WAR.  
*Fix:* add `src/main/resources/logback-spring.xml`.

**[arch-loop1-002] spring-idiom-field-injection — arch-drift**  
`MemberController.configKey` uses `@Value` field injection (`private String configKey`) instead of constructor injection — Spring idiom requires all injection through the constructor.  
*Fix:* move `@Value` parameter to the constructor signature.

---

## Parked Known Issues

None.

---

## Next Step

Emitting `validation-failed` → Migration agent will perform targeted rework (loop-2).
