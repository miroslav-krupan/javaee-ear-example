# Test & Behavior Analysis — kitchensink-ear
> **UNVERIFIED — build probe failed: `javax.*` APIs absent on Java 21 (javax.annotation, javax.xml.bind absent from classpath). No tests were compiled or run. This is a static characterisation only.**

Generated: 2026-09-01 · Issue: #7 · Analyst: test-behavior-analyst

---

## 1. Existing Test Inventory

| # | File | Framework | Type | Scope | Status |
|---|------|-----------|------|-------|--------|
| 1 | `ejb/src/test/java/.../MemberRegistrationIT.java` | Arquillian + JUnit 4 | Integration (container) | EJB | ❌ Cannot run — requires WildFly/JBoss container + managed datasource |

### MemberRegistrationIT — Detailed Characterisation

**Test:** `testRegister()` (single test method)

**What it does:**
- Creates a `Member` with name=`"Jane Doe"`, email=`"jane@mailinator.com"`, phoneNumber=`"2125551234"` — all valid inputs satisfying every constraint.
- Injects `MemberRegistration` via CDI and calls `register(member)`.
- Asserts `member.getId()` is not null after persistence (i.e., the entity received a generated PK).

**Deployment descriptor (via ShrinkWrap):**
- Deploys `Member`, `MemberRegistration`, `Resources` into a test WAR.
- Includes `META-INF/test-persistence.xml` (JTA datasource `java:jboss/datasources/KitchensinkEarQuickstartTestDS`, H2 in-memory, `hibernate.hbm2ddl.auto=create-drop`).
- No `beans.xml` content — just an empty marker for CDI activation.
- Deploys `test-ds.xml` as an unmanaged H2 datasource.

**Migration verdict:** This test cannot be migrated 1:1. Arquillian + ShrinkWrap + JBoss EAP container has no Spring Boot equivalent. The correct migration is a Spring Boot integration test using `@SpringBootTest` + `@DataJpaTest` + Testcontainers or H2.

---

## 2. Domain Model — Behavioural Constraints

### `Member` entity (`ejb/.../model/Member.java`)

Mapped to table `AA_Registrant`.

| Field | Type | Constraints | Column |
|-------|------|-------------|--------|
| `id` | `Long` | `@Id @GeneratedValue` — DB-assigned PK | `id` |
| `name` | `String` | `@NotNull`, `@Size(min=1, max=25)`, `@Pattern(regexp="[^0-9]*", message="Must not contain numbers")` | `name` |
| `email` | `String` | `@NotNull`, `@NotEmpty` (Hibernate), `@Email` (Hibernate) + DB UNIQUE constraint | `email` |
| `phoneNumber` | `String` | `@NotNull`, `@Size(min=10, max=12)`, `@Digits(fraction=0, integer=12)` | `phone_number` |

**Critical migration note:** `@Email` and `@NotEmpty` come from `org.hibernate.validator.constraints.*` (deprecated). Migration must replace with `jakarta.validation.constraints.Email` and `jakarta.validation.constraints.NotBlank`.

**JAXB annotation:** `@XmlRootElement` on `Member` — used for XML serialisation via JAXB (`javax.xml.bind`). No equivalent in Spring Boot by default; remove if only JSON is needed (Jackson handles it without annotation).

---

## 3. Business Logic Characterisation

### 3.1 `MemberRegistration.register(Member member)` — `ejb/.../service/MemberRegistration.java`

**Container contract:** `@Stateless` EJB — each call participates in a container-managed transaction. On `register()`, the transaction commits at method exit.

**Behaviour:**
1. Logs the member name at INFO level.
2. Calls `em.persist(member)` — inserts a new row; sets the generated `id` on the entity.
3. Fires a CDI `Event<Member>` — observers receive the persisted member (e.g., `MemberListProducer.onMemberListChanged()` to refresh the displayed list).

**Exception semantics:** Any JPA `PersistenceException` (e.g., duplicate email → unique constraint violation) propagates as an unchecked exception, causing the transaction to roll back. The caller (REST layer or JSF controller) is responsible for catching and mapping it.

**Migration target:** `@Service` or `@Transactional` Spring service. CDI event must be replaced — use Spring `ApplicationEvent` / `ApplicationEventPublisher` or direct service call.

### 3.2 `MemberRepository` — `ejb/.../data/MemberRepository.java`

**Container contract:** `@ApplicationScoped` CDI bean. Uses injected `EntityManager`.

| Method | Behaviour |
|--------|-----------|
| `findById(Long id)` | `em.find(Member.class, id)` — returns null if not found |
| `findByEmail(String email)` | JPA Criteria query, `getSingleResult()` — throws `NoResultException` if no match (callers must catch this) |
| `findAllOrderedByName()` | JPA Criteria query, ordered ascending by `name`; returns all rows |

**Migration target:** Spring Data JPA `MemberRepository extends JpaRepository<Member, Long>` — `findById`, `findByEmail`, `findAllByOrderByNameAsc` can be derived queries or `@Query`.

### 3.3 `MemberListProducer` — `ejb/.../data/MemberListProducer.java`

**Container contract:** `@RequestScoped` CDI bean.

**Behaviour:**
- `@PostConstruct retrieveAllMembersOrderedByName()` — loads all members ordered by name on each HTTP request.
- `@Produces @Named getMembers()` — exposes `List<Member>` as EL variable `members` for JSF views.
- `onMemberListChanged(@Observes ... Member)` — refreshes the list when a `Member` CDI event fires (from `MemberRegistration.register()`).

**Migration target:** In Spring Boot there is no JSF EL producer. This pattern becomes a model attribute in a Spring MVC `@Controller` or a REST endpoint response. The CDI observer pattern becomes a Spring `@EventListener`.

### 3.4 `MemberResourceRESTService` — `web/.../rest/MemberResourceRESTService.java`

Base path: `/members` (under `/kitchensink-ear-web` WAR context root).

| Endpoint | Method | Behaviour |
|----------|--------|-----------|
| `GET /members` | `listAllMembers()` | Returns all members ordered by name as JSON array |
| `GET /members/{id:[0-9][0-9]*}` | `lookupMemberById(long id)` | Returns single member or `404 NOT_FOUND` if null |
| `POST /members` | `createMember(Member)` | Validate → register → `200 OK`; or `400` on constraint violations (field→message map); or `409 CONFLICT` on duplicate email (`"Email taken"`); or `400` on any other exception (`"error"→message`) |

**Validation flow (`validateMember`):**
1. Run `Validator.validate(member)` — if any `ConstraintViolation` → throw `ConstraintViolationException` → `400` with field map.
2. Call `emailAlreadyExists(email)` — queries `repository.findByEmail(email)`, catches `NoResultException` to mean "not exists" → if email found, throw `ValidationException("Unique Email Violation")` → `409 CONFLICT`.

**Migration target:** Spring `@RestController` + `@Valid` on request body + `@ExceptionHandler` or `@ControllerAdvice` for constraint violations.

### 3.5 `MemberResourceRESTServiceSecond` — `web2/.../rest/MemberResourceRESTServiceSecond.java`

Identical REST API and logic to `MemberResourceRESTService` (same path `/members` under `/kitchensink-ear-web2` context root). No material behavioural differences.

### 3.6 `MemberController` — `web/.../controller/MemberController.java`

**Container contract:** `@Model` (CDI `@RequestScoped` + `@Named`).

**Behaviour:**
- `@PostConstruct initNewMember()`: initialises `newMember = new Member()`. Also serialises a test `Member` to JSON using Gson (dead-code artefact — `testMember` is created and serialised but the result `s` is only logged and discarded; it has no effect on application behaviour).
- `register()`: calls `memberRegistration.register(newMember)`, adds a success `FacesMessage`; on exception walks the cause chain to find root message and adds an error `FacesMessage`.
- `@Produces @Named getNewMember()`: exposes `newMember` as EL variable for JSF form binding.

**Migration target:** Spring MVC `@Controller` + Thymeleaf (or REST API). Gson import is dead code and should be removed.

### 3.7 `MemberControllerSecond` — `web2/.../controller/MemberControllerSecond.java`

Same as `MemberController` except:
- Uses `org.apache.deltaspike.core.api.config.ConfigResolver.getPropertyValue("config.key", "Default value")` in `initNewMember()` — reads an external config key at startup.
- No Gson import.

**Migration target:** Replace `ConfigResolver` with Spring `@Value("${config.key:Default value}")`. DeltaSpike has no Spring Boot equivalent and must be removed.

---

## 4. Coverage Gap List (for migration to fill)

The following behaviours have NO automated test coverage. The migration team MUST add tests for each before declaring the migrated app correct.

| # | Component | Behaviour to test | Priority |
|---|-----------|-------------------|----------|
| 1 | `Member` | Valid member satisfies all constraints (name, email, phone) | HIGH |
| 2 | `Member` | `name` with digits fails `@Pattern` | HIGH |
| 3 | `Member` | `name` blank (`""`) fails `@Size(min=1)` | HIGH |
| 4 | `Member` | `name` > 25 chars fails `@Size(max=25)` | HIGH |
| 5 | `Member` | Invalid email format fails `@Email` | HIGH |
| 6 | `Member` | Blank email fails `@NotBlank` (migrated from `@NotEmpty`) | HIGH |
| 7 | `Member` | `phoneNumber` < 10 digits fails `@Size(min=10)` | HIGH |
| 8 | `Member` | `phoneNumber` > 12 digits fails `@Size(max=12)` | HIGH |
| 9 | `Member` | `phoneNumber` with non-digits fails `@Digits` | HIGH |
| 10 | `MemberRegistration` | `register()` persists a valid member and fires an event | HIGH |
| 11 | `MemberRegistration` | `register()` with duplicate email causes rollback (DB constraint) | HIGH |
| 12 | `MemberRepository` | `findById()` returns null for unknown id | MEDIUM |
| 13 | `MemberRepository` | `findByEmail()` throws `NoResultException` for unknown email | MEDIUM |
| 14 | `MemberRepository` | `findAllOrderedByName()` returns results in ascending alphabetical order | MEDIUM |
| 15 | `MemberResourceRESTService` | `GET /members` returns JSON array of all members | HIGH |
| 16 | `MemberResourceRESTService` | `GET /members/{id}` returns member JSON for known id | HIGH |
| 17 | `MemberResourceRESTService` | `GET /members/{id}` returns 404 for unknown id | HIGH |
| 18 | `MemberResourceRESTService` | `POST /members` with valid body returns 200 | HIGH |
| 19 | `MemberResourceRESTService` | `POST /members` with invalid bean (bad phone) returns 400 with field errors | HIGH |
| 20 | `MemberResourceRESTService` | `POST /members` with duplicate email returns 409 `"Email taken"` | HIGH |
| 21 | `MemberResourceRESTServiceSecond` | Same 6 REST scenarios as #15–20 (separate context root) | MEDIUM |
| 22 | `MemberController.register()` | Success path: `FacesMessage` severity=INFO added | MEDIUM |
| 23 | `MemberController.register()` | Failure path: root cause message in `FacesMessage` severity=ERROR | MEDIUM |
| 24 | `MemberControllerSecond` | `initNewMember()` reads config key via Spring `@Value` (DeltaSpike removed) | MEDIUM |
| 25 | `emailAlreadyExists()` | Returns `true` when `findByEmail` returns a member | MEDIUM |
| 26 | `emailAlreadyExists()` | Returns `false` when `findByEmail` throws `NoResultException` | MEDIUM |

---

## 5. Third-Party Dependencies Requiring Migration Decisions

| Library | Usage | Migration action |
|---------|-------|-----------------|
| Arquillian | Test framework | Remove — replace with `@SpringBootTest` / `@DataJpaTest` |
| ShrinkWrap | Test deployment builder | Remove |
| `org.hibernate.validator.constraints.Email` / `NotEmpty` | Bean validation | Replace with `jakarta.validation.constraints.Email` / `NotBlank` |
| `javax.xml.bind.annotation.XmlRootElement` | JAXB/XML on `Member` | Remove if JSON-only; add `jakarta.xml.bind` if XML needed |
| `com.google.gson.Gson` | Dead code in `MemberController` | Remove |
| `org.apache.deltaspike.core.api.config.ConfigResolver` | Config in `MemberControllerSecond` | Replace with `@Value("${config.key:Default value}")` |
| WebLogic descriptors (`weblogic-application.xml`, `weblogic.xml`) | WebLogic session / classloader config | Remove — no Spring Boot equivalent; session config via `server.servlet.session.*` |
