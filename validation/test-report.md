# Test & Behavioral Validation Report

**Date:** 2026-09-01  
**Target module:** `kitchensink-springboot`  
**Branch:** `migration/springboot`  
**Tool:** `mvn test` (Maven Surefire / JUnit 5)

---

## Surefire Summary

| Test class | Tests | Failures | Errors | Skipped |
|---|---|---|---|---|
| `KitchensinkApplicationContextTest` | 1 | 0 | 0 | 0 |
| `MemberValidationTest` | 12 | 0 | 0 | 0 |
| `MemberRepositoryTest` | 5 | 0 | 0 | 0 |
| `MemberRegistrationTest` | 1 | 0 | 0 | 0 |
| `MemberRestControllerTest` | 8 | 0 | 0 | 0 |
| `MemberControllerTest` | 5 | 0 | 0 | 0 |
| **TOTAL** | **32** | **0** | **0** | **0** |

**Result: all green ✓**

---

## Test Classification Against `reverse_en/test-behavior.md`

### Migrated tests (carried over from original app)

| Test class :: method | Origin | Result |
|---|---|---|
| `MemberRegistrationTest :: registerSavesMemberAndPublishesEvent` | Migrated — replaces `MemberRegistrationIT.testRegister()` (Arquillian → JUnit 5 + Mockito) | pass |

### New gap-coverage tests

| Test class :: method | Gap # | Result |
|---|---|---|
| `MemberValidationTest :: validNamePasses` | 1 | pass |
| `MemberValidationTest :: nameWithDigitsIsRejected` | 2 | pass |
| `MemberValidationTest :: emptyNameIsRejected` | 3 | pass |
| `MemberValidationTest :: nullNameIsRejected` | 3 | pass |
| `MemberValidationTest :: nameTooLongIsRejected` | 4 | pass |
| `MemberValidationTest :: validEmailPasses` | 5 | pass |
| `MemberValidationTest :: malformedEmailIsRejected` | 6 | pass |
| `MemberValidationTest :: nullEmailIsRejected` | 7 | pass |
| `MemberValidationTest :: emptyEmailIsRejected` | 7 | pass |
| `MemberValidationTest :: phoneTooShortIsRejected` | 8 | pass |
| `MemberValidationTest :: phoneTooLongIsRejected` | 9 | pass |
| `MemberValidationTest :: phoneWithNonDigitsIsRejected` | 10 | pass |
| `MemberRepositoryTest :: findByEmailReturnsMatchingMember` | 11 | pass |
| `MemberRepositoryTest :: findByEmailReturnsEmptyWhenNotFound` | 12 | pass |
| `MemberRepositoryTest :: findAllByOrderByNameAscReturnsMembersInOrder` | 13 | pass |
| `MemberRepositoryTest :: findByIdReturnsEmptyWhenNotFound` | 14 | pass |
| `MemberRepositoryTest :: savedMemberIsRetrievableById` | 14 (positive case) | pass |
| `MemberRestControllerTest :: listAllMembers_returnsJsonList` | 16 | pass |
| `MemberRestControllerTest :: lookupById_notFound_returns404` | 17 | pass |
| `MemberRestControllerTest :: lookupById_found_returns200WithMember` | 17 (positive) | pass |
| `MemberRestControllerTest :: createMember_validPayload_returns200` | 18 | pass |
| `MemberRestControllerTest :: createMember_invalidPayload_returns400WithViolationMap` | 19 | pass |
| `MemberRestControllerTest :: createMember_duplicateEmail_returns409` | 20 | pass |
| `MemberRestControllerTest :: createMember_genericException_returns400WithErrorKey` | 21 | pass |
| `MemberRestControllerTest :: emailAlreadyExists_trueWhenPresent_falseWhenAbsent` | 22 | pass |
| `MemberControllerTest :: register_success_redirectsWithFlashMessage` | 23 | pass |
| `MemberControllerTest :: register_exception_addsErrorMessageToModel` | 24 | pass |
| `MemberControllerTest :: showRegistrationForm_returnsIndexWithEmptyMember` | (smoke) | pass |
| `MemberControllerTest :: register_validationError_returnsFormWithErrors` | (extra) | pass |
| `MemberControllerTest :: register_duplicateEmail_returns409FieldError` | (extra) | pass |
| `KitchensinkApplicationContextTest :: contextLoadsAndBeansWireUp` | (integration gate) | pass |

**1 migrated + 31 new = 32 tests, all green: yes**

---

## Gap List Status

| Gap # | Description | Covered? |
|---|---|---|
| 1 | Valid name passes | ✓ |
| 2 | Name with digits rejected | ✓ |
| 3 | Empty/null name rejected | ✓ |
| 4 | Name >25 chars rejected | ✓ |
| 5 | Valid email accepted | ✓ |
| 6 | Malformed email rejected | ✓ |
| 7 | Null/empty email rejected | ✓ |
| 8 | Phone < 10 digits rejected | ✓ |
| 9 | Phone > 12 chars rejected | ✓ |
| 10 | Phone with non-digit chars rejected | ✓ |
| 11 | `findByEmail` returns correct member | ✓ |
| 12 | `findByEmail` returns empty when not found | ✓ |
| 13 | `findAllOrderedByName` ascending order | ✓ |
| 14 | `findById` returns empty when not found | ✓ |
| 15 | `register` persists entity and fires event | ✓ |
| 16 | `GET /members` returns JSON list | ✓ |
| 17 | `GET /members/{id}` returns 404 when not found | ✓ |
| 18 | `POST /members` valid → 200 OK | ✓ |
| 19 | `POST /members` invalid → 400 violation map | ✓ |
| 20 | `POST /members` duplicate email → 409 | ✓ |
| 21 | `POST /members` generic exception → 400 error key | ✓ |
| 22 | `emailAlreadyExists` returns true/false | ✓ |
| 23 | `register` success: INFO message + form reset | ✓ |
| 24 | `register` exception: ERROR message with root cause | ✓ |
| 25 | `MemberListProducer` observer refreshes list on event | **✗ MISSING** |
| 26 | `MemberResourceRESTServiceSecond` smoke test | ✓ (web2 merged into single controller) |

### Gaps still without a test

- **Gap 25** — `MemberListProducer` observer: The CDI `@Observes Member` pattern was not preserved as a Spring `@EventListener`. The migrated `MemberController` fetches the list fresh from the DB on every GET request, making the observer redundant architecturally. However, no dedicated test verifies event-driven list refresh behaviour. Raised as `minor` finding `f82eaf263d3d`.
