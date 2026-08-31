# Test & Behavior Analysis — kitchensink-ear

> **UNVERIFIED — build probe failed:** `mvn -q test-compile` on the ejb module fails under Java 21.
> Errors: `package javax.annotation does not exist`, `package javax.xml.bind.annotation does not exist`,
> `cannot find symbol: class XmlRootElement`, `cannot find symbol: class PostConstruct`.
> Root cause: Java EE 7 APIs (`javax.*` namespace) are not available on the Java 21 classpath without
> explicit dependencies (they were removed from the JDK in Java 11). No unit tests were compiled or run.
> This document is a **static characterisation only**.

---

## 1. Existing Test Inventory

| File | Module | Type | Framework | Status |
|---|---|---|---|---|
| `ejb/src/test/java/.../test/MemberRegistrationIT.java` | ejb | Integration (Arquillian) | JUnit 4 + Arquillian + ShrinkWrap | Cannot compile/run — container dependent |

**Total test files:** 1  
**Unit tests:** 0  
**Integration tests (container-bound):** 1  

### MemberRegistrationIT — quality assessment
- **Coverage:** single happy-path only (`testRegister` — valid member → persisted and ID assigned)
- **Weaknesses:**
  - No negative tests (duplicate email, invalid name/phone/email)
  - No boundary tests for constraint limits (`@Size(min=1,max=25)` for name, `@Size(min=10,max=12)` for phone)
  - No test for `@Pattern(regexp="[^0-9]*")` — name-must-not-contain-digits rule
  - No test for the CDI event fired after persist (`Event<Member>.fire()`)
- **Migration note:** Arquillian tests have no Spring Boot equivalent as-is; must be rewritten as `@SpringBootTest` / `@DataJpaTest` tests.

---

## 2. Behavioural Characterisation (static, from source)

### 2.1 Domain Model — `Member`

| Field | Type | Constraints | DB column |
|---|---|---|---|
| `id` | `Long` | `@Id @GeneratedValue` | PK, auto-generated |
| `name` | `String` | `@NotNull @Size(min=1,max=25) @Pattern(regexp="[^0-9]*")` | `name` |
| `email` | `String` | `@NotNull @NotEmpty @Email` (Hibernate Validator) | `email` — unique constraint |
| `phoneNumber` | `String` | `@NotNull @Size(min=10,max=12) @Digits(fraction=0,integer=12)` | `phone_number` |

- Table name: `AA_Registrant`  
- Unique constraint on `email` column  
- `@XmlRootElement` for JAXB serialisation (relevant for JAX-RS JSON/XML output)

### 2.2 MemberRepository

| Method | Behaviour |
|---|---|
| `findById(Long)` | `EntityManager.find` — returns null if not found |
| `findByEmail(String)` | JPA Criteria query — throws `NoResultException` if not found (single result expected) |
| `findAllOrderedByName()` | JPA Criteria query — all members ordered ascending by name |

**Edge cases:**
- `findByEmail` — if multiple rows share the same email (broken DB), `getSingleResult()` throws `NonUniqueResultException` (unchecked); callers swallow `NoResultException` but NOT `NonUniqueResultException` — unguarded failure path.
- `findById` — returns `null`, callers must null-check.

### 2.3 MemberRegistration (EJB `@Stateless`)

```
register(Member):
  1. log name
  2. em.persist(member)          — JTA transaction (container-managed via @Stateless)
  3. memberEventSrc.fire(member) — CDI event, observed by MemberListProducer
```

- Transaction boundary: the entire `@Stateless` method runs in one JTA transaction.
- Post-register: CDI event updates the member list producer (request-scoped observer).
- No explicit rollback logic — a JPA constraint violation would rollback the transaction automatically.

### 2.4 MemberResourceRESTService (web module, JAX-RS)

**Endpoints (context: `/kitchensink-ear-web/rest`):**

| Method | Path | Behaviour | Response |
|---|---|---|---|
| GET | `/members` | `findAllOrderedByName()` | 200 JSON array |
| GET | `/members/{id}` | `findById(id)`, null → 404 | 200 JSON or 404 |
| POST | `/members` | validate → register → 200 or error map | 200 / 400 / 409 |

**POST validation flow:**
1. Bean Validation (`validator.validate`) — if violations → 400 with field→message map
2. Duplicate email check via `emailAlreadyExists()` → catches `NoResultException`, returns null-check result
3. If email taken → `ValidationException` → 409 with `{"email": "Email taken"}`
4. If generic exception → 400 with `{"error": "<message>"}`
5. Happy path → `registration.register(member)` → 200

**ID path constraint:** `{id:[0-9][0-9]*}` — requires at least one digit, no letters.

### 2.5 MemberResourceRESTServiceSecond (web2 module)

Functionally identical to `MemberResourceRESTService` — deployed under `/kitchensink-ear-web2/rest/members`. No behavioural differences found. Differs only: no `logger.info` on GET listAllMembers.

### 2.6 MemberListProducer

- `@RequestScoped` CDI bean
- `@PostConstruct` loads all members ordered by name
- Observes `Member` CDI events (`IF_EXISTS`) to refresh the list
- Produces `@Named` `List<Member>` for EL access in JSF views

### 2.7 MemberController (JSF backing bean)

Not directly tested. Delegates to `MemberRegistration.register`. Handles exception display in JSF view. Standard JSF submit flow.

---

## 3. Coverage Gap List for Migration Team

The following behaviours have **no test coverage** and must be covered by the Spring Boot migration baseline:

| # | Area | Gap |
|---|---|---|
| 1 | `Member` validation | Name with digits must fail `@Pattern(regexp="[^0-9]*")` |
| 2 | `Member` validation | Name length boundaries: empty string, 25-char OK, 26-char fail |
| 3 | `Member` validation | Email format: invalid format must fail |
| 4 | `Member` validation | Phone length boundaries: 9-char fail, 10-char OK, 12-char OK, 13-char fail |
| 5 | `Member` validation | Phone with non-digits must fail `@Digits` |
| 6 | `MemberRepository` | `findByEmail` — not-found path returns `NoResultException` |
| 7 | `MemberRepository` | `findById` — not-found path returns null |
| 8 | `MemberResourceRESTService` | GET `/members/{id}` not-found → 404 |
| 9 | `MemberResourceRESTService` | POST with invalid fields → 400 with field→message map |
| 10 | `MemberResourceRESTService` | POST with duplicate email → 409 |
| 11 | `MemberResourceRESTService` | POST happy path → 200 |
| 12 | `MemberRegistration` | CDI event fired after persist (verifiable in Spring via ApplicationEventPublisher) |
| 13 | `MemberListProducer` | List refreshes when member event is received |
| 14 | `MemberRepository` | `NonUniqueResultException` propagation on duplicate-email DB state (unguarded path) |

---

## 4. Migration Mapping Notes

| Old (JEE 7) | New (Spring Boot 3.4 / Jakarta) |
|---|---|
| `MemberRegistrationIT` (Arquillian) | Rewrite as `@DataJpaTest` (repo) + `@SpringBootTest` (service layer) |
| `javax.validation.*` | `jakarta.validation.*` |
| `javax.annotation.PostConstruct` | `jakarta.annotation.PostConstruct` |
| `javax.xml.bind.annotation.XmlRootElement` | Remove (not needed for JSON; use Jackson) |
| `@NotEmpty` (Hibernate Validator legacy) | `@NotBlank` (Jakarta standard) |
| `@Email` (Hibernate Validator legacy) | `@Email` (Jakarta Validation — same name, different import) |
| JUnit 4 (`@RunWith`) | JUnit 5 (`@ExtendWith`) |
| Arquillian `@Deployment` + ShrinkWrap | Spring Boot test slices (`@DataJpaTest`, `@WebMvcTest`) |
| `Event<Member>` CDI event | `ApplicationEventPublisher` + `@EventListener` |
