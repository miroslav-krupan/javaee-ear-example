# Test Behaviour Analysis

> **STATUS: UNVERIFIED — build probe failed: `javax.*` APIs absent on Java 21**
> Static characterisation only. No new unit tests were written or executed.
> Run produced on issue #11 / loop-1 (2026-09-01).

---

## 1. Build Probe Result

```
cd original_app && mvn -q test-compile
→ FAILED
   [ERROR] package javax.annotation does not exist
   [ERROR] package javax.xml.bind.annotation does not exist
   [ERROR] cannot find symbol: class XmlRootElement
   [ERROR] cannot find symbol: class PostConstruct
```

Root cause: Java EE 7 app uses `javax.*` namespace throughout; those packages are absent on Java 21 (removed from JDK). The Maven toolchain in this environment also resolves to Java 21. This is expected and is the reason for migration.

---

## 2. Existing Test Inventory

| File | Type | Framework | Status |
|---|---|---|---|
| `ejb/src/test/java/…/test/MemberRegistrationIT.java` | Integration test | Arquillian + JUnit 4 | Cannot run on Java 21; requires WildFly container |

### MemberRegistrationIT — what it tests
- Deploys a micro-archive (ShrinkWrap WAR) with `Member`, `MemberRegistration`, `Resources`, a test `persistence.xml`, and a test datasource.
- `@Inject`s `MemberRegistration` and `Logger` into the test.
- **Single test method `testRegister()`**: creates a `Member("Jane Doe", "jane@mailinator.com", "2125551234")`, calls `memberRegistration.register(newMember)`, then asserts `newMember.getId() != null` (i.e. JPA generated the PK after persist).

**Coverage provided:** happy-path member registration with JPA persistence, CDI wiring, and CMT transaction. Covers no validation paths, no query paths, and no error scenarios.

---

## 3. Application Behaviour Map

### 3.1 Domain Model — `Member`

| Field | Type | Constraints |
|---|---|---|
| `id` | `Long` | `@Id @GeneratedValue` — assigned by JPA on first persist |
| `name` | `String` | `@NotNull @Size(min=1, max=25) @Pattern(regexp="[^0-9]*", message="Must not contain numbers")` |
| `email` | `String` | `@NotNull @NotEmpty @Email` (Hibernate Validator); unique column in DB |
| `phoneNumber` | `String` | `@NotNull @Size(min=10, max=12) @Digits(fraction=0, integer=12)` — numeric string, 10–12 chars |

Table: `AA_Registrant`. Unique constraint on `email` column.

### 3.2 Repository — `MemberRepository` (`@ApplicationScoped`)

| Method | Behaviour |
|---|---|
| `findById(Long id)` | `em.find(Member.class, id)` — returns `null` if not found |
| `findByEmail(String email)` | JPQL Criteria `WHERE email = ?` via `getSingleResult()` — throws `NoResultException` if not found |
| `findAllOrderedByName()` | Criteria `ORDER BY name ASC` — returns all members sorted |

### 3.3 List Producer — `MemberListProducer` (`@RequestScoped`)

- `@PostConstruct retrieveAllMembersOrderedByName()`: populates `members` list from `MemberRepository.findAllOrderedByName()`.
- `@Produces @Named getMembers()`: exposes the list to EL as `#{members}`.
- `onMemberListChanged(@Observes Member)`: CDI event observer — refreshes the list whenever a `Member` event is fired (i.e. after any registration).

### 3.4 Service — `MemberRegistration` (`@Stateless` EJB)

- `register(Member member)`: persists via injected `EntityManager` (CMT transaction), then fires a CDI `Event<Member>` to notify `MemberListProducer`.
- No explicit rollback logic; transaction rolls back on unchecked exception.

### 3.5 REST — `MemberResourceRESTService` (web module, `@Path("/members")`)

| Method | Path | Description |
|---|---|---|
| `GET /members` | lists all | Calls `repository.findAllOrderedByName()`, returns JSON array |
| `GET /members/{id}` | by id | Calls `repository.findById(id)`, throws `404 WebApplicationException` if null |
| `POST /members` | create | Validates bean, calls `registration.register()`, returns `200 OK`; on constraint violations → `400` with field-error map; on duplicate email → `409 Conflict {"email":"Email taken"}`; on other exception → `400 {"error":"<msg>"}` |

`emailAlreadyExists(email)`: calls `repository.findByEmail()`, returns `true` if member found (catches `NoResultException` → `false`).

### 3.6 REST — `MemberResourceRESTServiceSecond` (web2 module, `@Path("/members")`)

Identical behaviour to `MemberResourceRESTService` above. Deployed in the `web2` WAR as a second endpoint (different context root). No differences in logic.

### 3.7 JSF Controller — `MemberController` (`@Model`, web module)

- `@PostConstruct initNewMember()`: initialises `newMember = new Member()`. Also contains **dead-code** Gson usage: constructs a `testMember` with only an email, serialises it to JSON, and logs it — has no functional effect on registration.
- `register()`: calls `memberRegistration.register(newMember)`; on success adds an `INFO FacesMessage("Registered!")` and resets `newMember`; on failure adds an `ERROR FacesMessage` with `getRootErrorMessage(e)`.
- `getRootErrorMessage(Exception e)`: walks the exception cause chain to the root, returns its localised message. Falls back to "Registration failed. See server log for more information" if `e == null`.

### 3.8 JSF Controller — `MemberControllerSecond` (web2 module)

Mirrors `MemberController` but deployed in `web2`. Same behaviour including the Gson dead-code block.

---

## 4. Third-Party Dependencies (migration impact)

| Library | Legacy artifact | Migration target |
|---|---|---|
| Gson | `com.google.code.gson:gson` | Keep as-is (only used for dead-code log in controller init) |
| Hibernate Validator | bundled with WildFly; `@Email`, `@NotEmpty` | Replace `@Email`→`jakarta.validation.constraints.Email`, `@NotEmpty`→`@NotBlank` or `@NotEmpty` from `jakarta.validation` |
| Arquillian | `arquillian-junit-container` + ShrinkWrap | Delete — cannot migrate to Spring Boot unit tests; replace with JUnit 5 + Mockito |
| SLF4J | `org.slf4j:slf4j-api` | Keep; Spring Boot auto-configures it |

---

## 5. Coverage Gap List (for migration to fill)

These behaviours are not covered by any runnable test and must have Spring Boot unit tests added post-migration:

| # | Component | Behaviour not covered |
|---|---|---|
| 1 | `Member` | `name` validation — blank/null rejected |
| 2 | `Member` | `name` pattern — numeric chars rejected |
| 3 | `Member` | `name` size — max 25 chars enforced |
| 4 | `Member` | `email` null/empty rejected |
| 5 | `Member` | `email` format validation |
| 6 | `Member` | `phoneNumber` null rejected |
| 7 | `Member` | `phoneNumber` non-digits rejected |
| 8 | `Member` | `phoneNumber` too short (<10) rejected |
| 9 | `Member` | `phoneNumber` too long (>12) rejected |
| 10 | `MemberRepository` | `findById` returns entity by PK |
| 11 | `MemberRepository` | `findById` returns null for unknown PK |
| 12 | `MemberRepository` | `findByEmail` returns entity by email |
| 13 | `MemberRepository` | `findByEmail` throws `NoResultException` for unknown email |
| 14 | `MemberRepository` | `findAllOrderedByName` returns list sorted by name ascending |
| 15 | `MemberListProducer` | `@PostConstruct` populates members on request start |
| 16 | `MemberListProducer` | `onMemberListChanged` refreshes list on CDI event |
| 17 | `MemberRegistration` | `register` persists member and fires CDI event (covered only by container IT — needs plain unit test with mocks) |
| 18 | `MemberResourceRESTService` | `GET /members` returns JSON array |
| 19 | `MemberResourceRESTService` | `GET /members/{id}` returns member JSON |
| 20 | `MemberResourceRESTService` | `GET /members/{id}` returns 404 for unknown id |
| 21 | `MemberResourceRESTService` | `POST /members` happy path → 200 |
| 22 | `MemberResourceRESTService` | `POST /members` constraint violation → 400 with field-error map |
| 23 | `MemberResourceRESTService` | `POST /members` duplicate email → 409 `{"email":"Email taken"}` |
| 24 | `MemberResourceRESTService` | `emailAlreadyExists` true/false logic |
| 25 | `MemberController` | `register()` happy path — JSF success message, newMember reset |
| 26 | `MemberController` | `register()` error path — JSF error message with root cause message |
