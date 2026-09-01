# Test Report — kitchensink-springboot

> Generated: 2026-09-01 | Loop: loop-1 | Issue: #11
> Validator: test-behavioral | Module: `kitchensink-springboot/`

---

## 1. Test Run Summary

**Command:** `mvn clean test` (clean build required — see §4)

| Result | Count |
|---|---|
| Tests run | 46 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 0 |
| **Overall** | **ALL GREEN** |

### Surefire class summary

| Test class | Tests | Result |
|---|---|---|
| `MemberSchemaValidationTest` | 1 | PASS |
| `config.SecurityConfigContextLoadTest` | 3 | PASS |
| `model.MemberValidationTest` | 14 | PASS |
| `repository.MemberRepositoryTest` | 5 | PASS |
| `service.MemberRegistrationContextLoadTest` | 1 | PASS |
| `service.MemberRegistrationServiceTest` | 2 | PASS |
| `web.rest.MemberRestControllerContextLoadTest` | 2 | PASS |
| `web.rest.MemberRestControllerTest` | 8 | PASS |
| `web.ui.MemberControllerContextLoadTest` | 2 | PASS |
| `web.ui.MemberControllerTest` | 8 | PASS |
| **Total** | **46** | **PASS** |

---

## 2. Test Classification

| Test class :: method | Origin | Result |
|---|---|---|
| `service.MemberRegistrationServiceTest :: register_persistsMemberAndPublishesEvent` | **Migrated** (from Arquillian `MemberRegistrationIT.testRegister()`) | PASS |
| `service.MemberRegistrationServiceTest :: register_throwsEmailAlreadyExistsException_whenEmailTaken` | New (gap-coverage — Gap 17 variant) | PASS |
| `model.MemberValidationTest :: name_nullIsRejected` | New (gap-coverage — Gap 1) | PASS |
| `model.MemberValidationTest :: name_blankIsRejected` | New (gap-coverage — Gap 1) | PASS |
| `model.MemberValidationTest :: name_numericCharsRejected` | New (gap-coverage — Gap 2) | PASS |
| `model.MemberValidationTest :: name_tooLongIsRejected` | New (gap-coverage — Gap 3) | PASS |
| `model.MemberValidationTest :: name_exactly25CharsIsAccepted` | New (gap-coverage — Gap 3) | PASS |
| `model.MemberValidationTest :: email_nullIsRejected` | New (gap-coverage — Gap 4) | PASS |
| `model.MemberValidationTest :: email_emptyIsRejected` | New (gap-coverage — Gap 4) | PASS |
| `model.MemberValidationTest :: email_invalidFormatIsRejected` | New (gap-coverage — Gap 5) | PASS |
| `model.MemberValidationTest :: email_validFormatAccepted` | New (gap-coverage — Gap 5) | PASS |
| `model.MemberValidationTest :: phoneNumber_nullIsRejected` | New (gap-coverage — Gap 6) | PASS |
| `model.MemberValidationTest :: phoneNumber_nonDigitsRejected` | New (gap-coverage — Gap 7) | PASS |
| `model.MemberValidationTest :: phoneNumber_tooShortIsRejected` | New (gap-coverage — Gap 8) | PASS |
| `model.MemberValidationTest :: phoneNumber_tooLongIsRejected` | New (gap-coverage — Gap 9) | PASS |
| `model.MemberValidationTest :: validMember_hasNoViolations` | New (general validity check) | PASS |
| `repository.MemberRepositoryTest :: findById_returnsEntityWhenFound` | New (gap-coverage — Gap 10) | PASS |
| `repository.MemberRepositoryTest :: findById_returnsNullWhenNotFound` | New (gap-coverage — Gap 11) | PASS |
| `repository.MemberRepositoryTest :: findByEmail_returnsEntityWhenFound` | New (gap-coverage — Gap 12) | PASS |
| `repository.MemberRepositoryTest :: findByEmail_throwsNoResultExceptionWhenNotFound` | New (gap-coverage — Gap 13) | PASS |
| `repository.MemberRepositoryTest :: findAllOrderedByName_returnsSortedByNameAscending` | New (gap-coverage — Gap 14) | PASS |
| `web.ui.MemberControllerTest :: getIndex_populatesMembersList` | New (gap-coverage — Gap 15) | PASS |
| `web.ui.MemberControllerTest :: getIndex_eachRequestFetchesFreshList` | New (gap-coverage — Gap 16) | PASS |
| `web.rest.MemberRestControllerTest :: getAll_returnsJsonArray` | New (gap-coverage — Gap 18) | PASS |
| `web.rest.MemberRestControllerTest :: getById_foundReturns200` | New (gap-coverage — Gap 19) | PASS |
| `web.rest.MemberRestControllerTest :: getById_notFoundReturns404` | New (gap-coverage — Gap 20) | PASS |
| `web.rest.MemberRestControllerTest :: create_happyPath_returns200` | New (gap-coverage — Gap 21) | PASS |
| `web.rest.MemberRestControllerTest :: create_constraintViolation_returns400WithFieldErrors` | New (gap-coverage — Gap 22) | PASS |
| `web.rest.MemberRestControllerTest :: create_duplicateEmail_returns409` | New (gap-coverage — Gap 23) | PASS |
| `web.rest.MemberRestControllerTest :: create_emailAlreadyExists_triggers409` | New (gap-coverage — Gap 24) | PASS |
| `web.rest.MemberRestControllerTest :: create_emailNotExists_succeeds200` | New (gap-coverage — Gap 24) | PASS |
| `web.ui.MemberControllerTest :: register_happyPath_redirectsWithSuccessMessage` | New (gap-coverage — Gap 25) | PASS |
| `web.ui.MemberControllerTest :: register_errorPath_showsErrorMessage` | New (gap-coverage — Gap 26) | PASS |
| `web.ui.MemberControllerTest :: register_errorPath_extractsRootCauseMessage` | New (gap-coverage — Gap 26 variant) | PASS |
| `web.ui.MemberControllerTest :: register_validationFailure_returnsFormView` | New (validation integration) | PASS |
| `web.ui.MemberControllerTest :: getIndex2_populatesMembersList` | New (V2 controller — Gap 15 equivalent) | PASS |
| `web.ui.MemberControllerTest :: register_v2_happyPath_redirectsWithSuccessMessage` | New (V2 controller — Gap 25 equivalent) | PASS |
| `MemberSchemaValidationTest :: entityMappingsLoadAgainstFlywaySchema` | New (schema integrity) | PASS |
| `config.SecurityConfigContextLoadTest :: securityFilterChainWiresUp` | New (wiring check) | PASS |
| `config.SecurityConfigContextLoadTest :: passwordEncoderWiresUp` | New (wiring check) | PASS |
| `config.SecurityConfigContextLoadTest :: passwordEncoderIsBCrypt` | New (wiring check) | PASS |
| `service.MemberRegistrationContextLoadTest :: memberRegistrationServiceWiresUp` | New (wiring check) | PASS |
| `web.rest.MemberRestControllerContextLoadTest :: memberRestControllerWiresUp` | New (wiring check) | PASS |
| `web.rest.MemberRestControllerContextLoadTest :: memberRestControllerV2WiresUp` | New (wiring check) | PASS |
| `web.ui.MemberControllerContextLoadTest :: memberControllerWiresUp` | New (wiring check) | PASS |
| `web.ui.MemberControllerContextLoadTest :: memberControllerV2WiresUp` | New (wiring check) | PASS |

**1 migrated + 45 new = 46 tests, all green? YES**

---

## 3. Coverage Gap List — Status

| Gap # | Component | Behaviour | Status |
|---|---|---|---|
| 1 | `Member` | `name` blank/null rejected | Covered |
| 2 | `Member` | `name` pattern — numeric chars rejected | Covered |
| 3 | `Member` | `name` size — max 25 chars | Covered |
| 4 | `Member` | `email` null/empty rejected | Covered |
| 5 | `Member` | `email` format validation | Covered |
| 6 | `Member` | `phoneNumber` null rejected | Covered |
| 7 | `Member` | `phoneNumber` non-digits rejected | Covered |
| 8 | `Member` | `phoneNumber` too short (<10) rejected | Covered |
| 9 | `Member` | `phoneNumber` too long (>12) rejected | Covered |
| 10 | `MemberRepository` | `findById` returns entity by PK | Covered |
| 11 | `MemberRepository` | `findById` returns null for unknown PK | Covered |
| 12 | `MemberRepository` | `findByEmail` returns entity by email | Covered |
| 13 | `MemberRepository` | `findByEmail` throws `NoResultException` for unknown email | Covered |
| 14 | `MemberRepository` | `findAllOrderedByName` returns list sorted by name asc | Covered |
| 15 | `MemberListProducer` | `@PostConstruct` populates members (merged into controller) | Covered |
| 16 | `MemberListProducer` | `onMemberListChanged` refreshes list on each request | Covered |
| 17 | `MemberRegistration` | `register` persists member and fires event | Covered |
| 18 | `MemberResourceRESTService` | `GET /members` returns JSON array | Covered |
| 19 | `MemberResourceRESTService` | `GET /members/{id}` returns member JSON | Covered |
| 20 | `MemberResourceRESTService` | `GET /members/{id}` returns 404 for unknown id | Covered |
| 21 | `MemberResourceRESTService` | `POST /members` happy path → 200 | Covered |
| 22 | `MemberResourceRESTService` | `POST /members` constraint violation → 400 | Covered |
| 23 | `MemberResourceRESTService` | `POST /members` duplicate email → 409 | Covered |
| 24 | `MemberResourceRESTService` | `emailAlreadyExists` true/false logic | Covered |
| 25 | `MemberController` | `register()` happy path — success message, newMember reset | Covered |
| 26 | `MemberController` | `register()` error path — error message with root cause | Covered |

**Gap items still without a test: NONE**

---

## 4. Build Note — Stale Artifact

Running `mvn test` **without** a prior clean produces 14 ERROR tests across 6 classes due to a duplicate Flyway V1 migration in `target/classes/db/migration/`:
- `V1__init.sql` (current source migration)
- `V1__create_aa_registrant.sql` (stale artifact from a renamed prior migration)

The stale file is not present in `src/main/resources/db/migration/` and is not tracked by git. Running `mvn clean test` clears it and all 46 tests pass. CI pipelines that run `clean` are unaffected. This is a minor local developer ergonomics issue.
