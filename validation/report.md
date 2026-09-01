# Validation Report — kitchensink-springboot

> Loop: **loop-2** | Issue: [#11](https://github.com/miroslav-krupan/javaee-ear-example/issues/11) | Generated: 2026-09-01T18:30:10Z
> Gate result: **PASS** — 0 open blocker, 0 open major

---

## Validator Summary

| Validator | Loop-2 Findings | Result |
|---|---|---|
| Build & Startup | blocker resolved ✔ | ✅ PASS |
| Test & Behavioral | minor resolved ✔ | ✅ PASS |
| Architecture | 1 minor (open) | ⚠️ minor only |
| Security | 0 | ✅ PASS |

---

## Open Findings

### 🟡 Minor (non-blocking known-issues)

| ID | Validator | Location | Summary |
|---|---|---|---|
| `cba19eade379` | architecture | `kitchensink-springboot/src/main/resources/application-prod.properties` | `application-prod.properties` absent; §5 layout lists it as a resource for prod datasource override. Optional file — no prod deployment impact until a prod profile is needed. |

---

## Resolved This Pass

| ID | Validator | Severity | Resolution |
|---|---|---|---|
| `d07430d36078` | build-startup | **blocker** | Stale `V1__create_aa_registrant.sql` eliminated — `target/` added to `.gitignore`. `mvn verify` now passes cleanly. |
| `bfeb8fb7aca8` | test-behavioral | minor | Same stale artifact gone; `mvn clean test` and `mvn test` both pass 46/46. |

---

## Tests

See full test report: [`validation/test-report.md`](test-report.md)

### Summary (loop-2)

| Metric | Value |
|---|---|
| Command | `mvn clean test` |
| Tests run | 46 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| **Result** | **ALL GREEN** |

- **1 migrated** test (from Arquillian `MemberRegistrationIT.testRegister()`)
- **45 new** gap-coverage tests — all 26 gaps covered

---

## Parked Known-Issues

_None_ — no finding has been open across ≥3 loops.

---

## Gate Decision

**PASS** — zero open `blocker`, zero open `major`. PR marked ready-for-review.

**Documented known-issues (non-blocking, for human reviewer):**
- `cba19eade379` [minor] — `application-prod.properties` absent (optional prod-profile override file)
