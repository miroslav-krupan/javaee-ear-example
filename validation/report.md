# Validation Report — kitchensink-springboot

> Loop: **loop-1** | Issue: [#11](https://github.com/miroslav-krupan/javaee-ear-example/issues/11) | Generated: 2026-09-01T18:11:45Z
> Gate result: **FAIL** — 1 open blocker

---

## Validator Summary

| Validator | Findings | Result |
|---|---|---|
| Build & Startup | 1 blocker | ❌ FAIL |
| Test & Behavioral | 1 minor | ⚠️ minor only |
| Architecture | 1 minor | ⚠️ minor only |
| Security | 0 | ✅ PASS |

---

## Open Findings

### 🔴 Blocker

| ID | Validator | Location | Summary |
|---|---|---|---|
| `d07430d36078` | build-startup | `kitchensink-springboot/target/classes/db/migration` | `mvn verify` fails: stale `V1__create_aa_registrant.sql` in `target/` conflicts with `V1__init.sql`. `mvn clean verify` passes — stale artifact from a migration file rename, not a code defect. |

**Recommended fix:** Add/ensure a `<clean>` lifecycle execution before `verify` in CI, or delete the stale file from `target/`. The source migration directory (`src/main/resources/db/migration/`) only contains `V1__init.sql` — no code change needed, just a build hygiene fix (e.g. `.gitignore` entry or `maven-clean-plugin` config).

### 🟡 Minor

| ID | Validator | Location | Summary |
|---|---|---|---|
| `cba19eade379` | architecture | `kitchensink-springboot/src/main/resources/application-prod.properties` | `application-prod.properties` absent; §5 layout lists it as a resource for prod datasource override. |
| `bfeb8fb7aca8` | test-behavioral | `kitchensink-springboot/target/classes/db/migration` | Stale `V1__create_aa_registrant.sql` in `target/classes` causes `mvn test` (no clean) to fail with duplicate Flyway V1; `mvn clean test` passes all 46 tests. |

---

## Tests

See full test report: [`validation/test-report.md`](test-report.md)

### Summary

| Metric | Value |
|---|---|
| Command | `mvn clean test` |
| Tests run | 46 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| **Result** | **ALL GREEN** |

- **1 migrated** test (from Arquillian `MemberRegistrationIT.testRegister()`)
- **45 new** gap-coverage tests (all 26 gaps covered)
- All 46 tests pass with `mvn clean test`

> **Note:** `mvn test` without `clean` fails due to the same stale `target/` artifact (see blocker above). Standard CI `mvn clean verify` is unaffected.

---

## Parked Known-Issues

_None_ — this is loop-1; no chronic findings yet.

---

## Gate Decision

**FAIL** — 1 open `blocker`. Emitting `validation-failed` to wake Migration for targeted rework.

Next loop will be **loop-2**.
