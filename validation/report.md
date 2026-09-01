# Validation Report — loop-2

**Date:** 2026-09-01  
**Result:** REWORK REQUIRED — 5 open findings

---

## Validator Summary

| Validator | Result | Open Findings |
|---|---|---|
| Build & Startup | **PASS** | 0 (all 3 loop-1 findings resolved) |
| Test & Behavioral | **FAIL** | 4 (coverage gaps) |
| Architecture | **FAIL** | 1 (logback-spring.xml still absent) |
| Security | **PASS** | 0 |

---

## Resolved This Loop

| Finding | Validator | Summary |
|---|---|---|
| 850d5fb9 / mvn-verify | build-startup | `mvn verify` now passes — sequence + proxy fixed |
| 200a3c35 / request-scope-proxy | build-startup | MemberListModel proxy added — ScopeNotActiveException gone |
| 612a6209 / schema-validation | build-startup | IDENTITY strategy aligned — SchemaManagementException gone |
| arch-loop1-002 / field-injection | architecture | MemberController.configKey moved to constructor injection |
| F-TB-001 / mvn-test-pass | test-behavioral | 12/12 tests green (EXIT 0) |

---

## Open Findings

### Test & Behavioral

**[F-TB-002] coverage-completeness — test-coverage**  
Coverage gaps 1–8 still absent — no Bean Validation unit tests added for Member constraints.

**[F-TB-003] coverage-completeness — test-coverage**  
Coverage gap #9 still absent — no `findById` not-found test added.

**[F-TB-004] coverage-completeness — test-coverage**  
Coverage gap #12 still partial — `MemberRegistrationServiceTest` does not verify `MemberRegisteredEvent` publication.

**[F-TB-005] coverage-completeness — test-coverage**  
Coverage gap #19 still partial — no isolated unit test for `getRootErrorMessage` chained-exception extraction.

### Architecture

**[arch-loop2-001] cited-file-missing — arch-drift**  
`logback-spring.xml` still absent from `src/main/resources/` — target-architecture §6 prescribes it as mandatory. Carries forward from arch-loop1-001; no specialist addressed this in loop-2.

---

## Parked Known Issues

None.

---

## Next Step

Bumping to loop-3 (final allowed loop). Emitting `validation-failed` → Migration agent targeted rework.
