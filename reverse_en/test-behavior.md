# Test Behaviour Analysis — kitchensink-ear

> **UNVERIFIED — build probe failed: `javax.*` APIs absent on Java 21**
> `cd original_app && mvn -q test-compile` exits non-zero with:
> `package javax.annotation does not exist` / `package javax.xml.bind.annotation does not exist`
> This is expected for a Java EE 7 app running under JDK 21 (Java EE APIs removed from JDK 9+).
> All characterisation below is **static only** — no tests were run.

---

## 1. Existing Test Inventory

| Test class | Location | Framework | Scope |
|---|---|---|---|
| `MemberRegistrationIT` | `original_app/ejb/src/test/java/…/test/` | JUnit 4 + Arquillian | Container IT |

### `MemberRegistrationIT` — detail

- **Deployment**: `ShrinkWrap` WAR containing `Member`, `MemberRegistration`, `Resources` + `test-persistence.xml` + `test-ds.xml`.
- **Test**: `testRegister()` — creates `Member("Jane Doe", "jane@mailinator.com", "2125551234")`, calls `memberRegistration.register()`, asserts `id != null` after persist.
- **Verdict**: happy-path only, requires a running WildFly/JBoss container. Not executable as a plain JVM unit test. **No negative tests.**

### Test quality assessment

| Dimension | Score | Notes |
|---|---|---|
| Coverage | Very low | 1 Arquillian IT; all real logic untested at unit level |
| Isolation | None | Container-managed; cannot run headless |
| Negative paths | 0 | No validation-failure, duplicate-email, or 404 tests |
| Migration reuse | None | Arquillian + ShrinkWrap must be replaced by JUnit 5 + Mockito / Spring test slices |

---

## 2. Behavioural Characterisation

### 2.1 Domain model — `Member`

Table: `AA_Registrant` (unique constraint on `email`)

| Field | Type | Constraints |
|---|---|---|
| `id` | `Long` | `@Id @GeneratedValue` — assigned by JPA on persist |
| `name` | `String` | `@NotNull @Size(min=1,max=25) @Pattern(regexp="[^0-9]*")` — no digits |
| `email` | `String` | `@NotNull @NotEmpty @Email` (Hibernate Validator) + DB unique |
| `phoneNumber` | `String` | `@NotNull @Size(min=10,max=12) @Digits(fraction=0,integer=12)` |

`@XmlRootElement` present (JAXB — can be dropped when Jackson handles serialisation).

### 2.2 Service — `MemberRegistration`

`@Stateless` EJB (container-managed transaction, REQUIRED default).

| Method | Behaviour |
|---|---|
| `register(Member)` | `em.persist(member)` then fires `Event<Member>` — observers notified in same transaction |

### 2.3 Repository — `MemberRepository`

`@ApplicationScoped` CDI bean; `EntityManager` injected.

| Method | Behaviour |
|---|---|
| `findById(Long)` | `em.find()` — returns `null` if not found |
| `findByEmail(String)` | Criteria API `getSingleResult()` — throws `NoResultException` if not found |
| `findAllOrderedByName()` | Criteria API, ascending by `name` field |

### 2.4 CDI producer — `MemberListProducer`

`@RequestScoped`; populates `List<Member> members` via repository.

| Hook | Behaviour |
|---|---|
| `@PostConstruct retrieveAllMembersOrderedByName()` | Loads ordered list on bean creation |
| `@Observes(IF_EXISTS) Member` | Refreshes list when a Member CDI event fires (JSF page still open) |
| `@Produces @Named getMembers()` | Exposes list as `#{members}` EL variable |

### 2.5 REST API — `MemberResourceRESTService` (web WAR)

Base path: `/members`

| Endpoint | Method | Success | Error cases |
|---|---|---|---|
| `GET /members` | `listAllMembers()` | 200 JSON array (ordered by name) | — |
| `GET /members/{id:[0-9]+}` | `lookupMemberById(long)` | 200 JSON member | 404 if `findById` returns null |
| `POST /members` | `createMember(Member)` | 200 OK | 400 violation map on Bean Validation failure; 409 `{"email":"Email taken"}` on duplicate; 400 `{"error":"<msg>"}` on other exception |

`validateMember()` flow:
1. `validator.validate(member)` → `ConstraintViolationException` if violations
2. `emailAlreadyExists(email)` → calls `findByEmail`, catches `NoResultException`, returns `member != null`
3. `ValidationException("Unique Email Violation")` if duplicate found

`createViolationResponse()` maps each `ConstraintViolation` to `propertyPath → message`.

### 2.6 REST API — `MemberResourceRESTServiceSecond` (web2 WAR)

Identical structure to `MemberResourceRESTService` — second WAR deployment serving the same logic.

### 2.7 JSF controller — `MemberController` (web WAR)

`@Model` (request-scoped, EL-accessible as `#{memberController}`).

| Method | Behaviour |
|---|---|
| `@PostConstruct initNewMember()` | Creates empty `Member`; also contains Gson dead-code (serialises a test Member to JSON, logs it — no side effects) |
| `@Produces @Named getNewMember()` | Exposes `newMember` as `#{newMember}` for form binding |
| `register()` | Calls `memberRegistration.register(newMember)`; on success adds `FacesMessage(INFO, "Registered!")` and resets form via `initNewMember()`; on exception traverses cause chain via `getRootErrorMessage()` and adds `FacesMessage(ERROR, …)` |

### 2.8 JSF controller — `MemberControllerSecond` (web2 WAR)

Mirrors `MemberController` — same logic on the second WAR.

---

## 3. Coverage Gap List (migration must fill these)

Each gap = one missing unit-test behaviour for the migrated Spring Boot app.

| # | Class / method | Gap description |
|---|---|---|
| 1 | `Member` | Valid name (letters only, 1–25 chars) passes validation |
| 2 | `Member` | Name with digits rejected by `@Pattern` |
| 3 | `Member` | Empty/null name rejected by `@Size(min=1)` / `@NotNull` |
| 4 | `Member` | Name exceeding 25 chars rejected by `@Size(max=25)` |
| 5 | `Member` | Valid email accepted |
| 6 | `Member` | Malformed email rejected by `@Email` |
| 7 | `Member` | Null/empty email rejected by `@NotNull` / `@NotEmpty` |
| 8 | `Member` | Phone < 10 digits rejected by `@Size(min=10)` |
| 9 | `Member` | Phone > 12 chars rejected by `@Size(max=12)` |
| 10 | `Member` | Phone with non-digit chars rejected by `@Digits` |
| 11 | `MemberRepository` | `findByEmail` returns correct member on match |
| 12 | `MemberRepository` | `findByEmail` raises / returns empty when email not found |
| 13 | `MemberRepository` | `findAllOrderedByName` returns list in ascending name order |
| 14 | `MemberRepository` | `findById` returns `null`/`Optional.empty()` when id not found |
| 15 | `MemberRegistration` | `register` persists entity and fires application event |
| 16 | `MemberResourceRESTService` | `GET /members` returns all members as JSON list |
| 17 | `MemberResourceRESTService` | `GET /members/{id}` returns 404 when member not found |
| 18 | `MemberResourceRESTService` | `POST /members` with valid payload → 200 OK |
| 19 | `MemberResourceRESTService` | `POST /members` with invalid payload → 400 with violation map |
| 20 | `MemberResourceRESTService` | `POST /members` with duplicate email → 409 conflict |
| 21 | `MemberResourceRESTService` | `POST /members` generic exception → 400 with `error` key |
| 22 | `MemberResourceRESTService` | `emailAlreadyExists` returns `true` on duplicate, `false` otherwise |
| 23 | `MemberController` | `register` success adds INFO FacesMessage and resets form |
| 24 | `MemberController` | `register` exception adds ERROR FacesMessage with root cause |
| 25 | `MemberListProducer` | Observer refreshes member list on Member event |
| 26 | `MemberResourceRESTServiceSecond` | Mirrors web REST — at minimum `GET /members` smoke test |

---

## 4. Third-Party Dependency Migration Map

| Legacy (Java EE / javax) | Spring Boot 3.4 / Jakarta target |
|---|---|
| `javax.persistence.*` | `jakarta.persistence.*` |
| `javax.validation.*` | `jakarta.validation.*` |
| `javax.ejb.Stateless` | `@Service` + `@Transactional` (Spring) |
| `javax.enterprise.context.*` | Spring stereotype scopes |
| `javax.enterprise.event.Event` | `ApplicationEventPublisher` (Spring) |
| `javax.enterprise.inject.*` | Constructor injection / `@Autowired` |
| `javax.faces.*` (JSF) | Thymeleaf or Vue/React SPA + REST |
| `javax.ws.rs.*` (JAX-RS) | `@RestController` / `@RequestMapping` (Spring MVC) |
| `javax.xml.bind.*` (JAXB) | Remove — Jackson handles JSON serialisation |
| `javax.inject.*` | `@Autowired` / constructor injection |
| `org.hibernate.validator.*` (legacy constraints) | `jakarta.validation.*` (Hibernate Validator 8 ships Jakarta) |
| `com.google.gson.Gson` | Jackson (Spring Boot default); Gson dead-code in `MemberController` can be removed |
| `org.jboss.arquillian.*` + ShrinkWrap | JUnit 5 + Mockito + `@WebMvcTest` / `@DataJpaTest` |
| EAR + multiple WARs | Single Spring Boot fat JAR (web + web2 merged) |
