# Business Logic Analysis — kitchensink-ear

## Domain Entity: Member

**Class:** `org.jboss.as.quickstarts.kitchensink_ear.model.Member`
**Table:** `AA_Registrant`

| Field | Column | Type | Meaning |
|---|---|---|---|
| `id` | `id` | Long | Auto-generated primary key |
| `name` | `name` | String | Registrant's full name |
| `email` | `email` | String | Contact email, unique per registrant |
| `phoneNumber` | `phone_number` | String | Contact phone number |

---

## Business Rules and Validations

All rules are declared on `Member` via Bean Validation annotations and enforced both by the JSF layer (field messages) and programmatically by `MemberResourceRESTService.validateMember()`.

| Field | Rule | Annotation / Source |
|---|---|---|
| `name` | Not null; 1–25 characters; **must not contain digits** (`[^0-9]*` pattern, message "Must not contain numbers") | `@NotNull @Size(min=1,max=25) @Pattern(regexp="[^0-9]*")` |
| `email` | Not null; not empty; valid email format | `@NotNull @NotEmpty @Email` (Hibernate Validator) |
| `email` | **Unique across all registrants** — enforced at the DB level (`UniqueConstraint`) and at the application level before persist | `@UniqueConstraint(columnNames="email")` + `emailAlreadyExists()` query |
| `phoneNumber` | Not null; **10–12 characters**; **digits only** (no fraction, max 12 integer digits) | `@NotNull @Size(min=10,max=12) @Digits(fraction=0,integer=12)` |

---

## Core Use Cases

### UC-1: Register a Member

**Trigger:** User submits the registration form (JSF) or POSTs to the REST endpoint.

**Input:** name, email, phoneNumber.

**Steps:**
1. Bean Validation runs (field-level constraints above).
2. Email uniqueness is checked against the database (`findByEmail`).
3. If valid and unique: `MemberRegistration.register(member)` persists the entity inside a container-managed transaction (the EJB is `@Stateless`).
4. After persist, a CDI `Event<Member>` is fired — `MemberListProducer.onMemberListChanged()` observes it (with `Reception.IF_EXISTS`) to refresh the in-request member list cache.
5. The form is reset / a success response is returned.

**Failure paths:**

| Condition | JSF outcome | REST outcome |
|---|---|---|
| Bean Validation violation | Inline field error messages (JSF messages) | HTTP 400 with `{ fieldName: "error message", … }` |
| Email already registered | Exception propagates; root-cause message shown on form | HTTP 409 with `{ "email": "Email taken" }` |
| Other exception | Root-cause message shown on form | HTTP 400 with `{ "error": "message" }` |

**Observable REST behaviour (must be preserved exactly):**
- Success → `200 OK` with empty body (NOT 201 Created).
- The POST body is consumed as `application/json`; response is `application/json`.

---

### UC-2: List All Members

**Trigger:** Page load (JSF) or `GET /rest/members` / `GET /rest2/members` (REST).

**Behaviour:** Returns all `Member` rows from `AA_Registrant` ordered ascending by `name`.

**REST output:** JSON array of Member objects.

**JSF:** The `#{members}` EL list is populated at request start via `MemberListProducer.@PostConstruct`; the table is hidden when the list is empty.

---

### UC-3: Look Up a Member by ID

**Trigger:** `GET /rest/members/{id}` or `GET /rest2/members/{id}` (numeric IDs only; path regex `[0-9][0-9]*`).

**Behaviour:** Returns the matching `Member` as JSON; throws `404 Not Found` if no member with that id exists.

---

## REST API Surface

Two WAR deployments expose identical REST endpoints under different paths:

| Module | Context root | JAX-RS base | Endpoint | Method |
|---|---|---|---|---|
| `web` | `/kitchensink-ear-web` | `/rest` | `/members` | GET — list all |
| `web` | `/kitchensink-ear-web` | `/rest` | `/members/{id}` | GET — by id |
| `web` | `/kitchensink-ear-web` | `/rest` | `/members` | POST — register |
| `web2` | `/kitchensink-ear-web2` | `/rest2` | `/members` | GET — list all |
| `web2` | `/kitchensink-ear-web2` | `/rest2` | `/members/{id}` | GET — by id |
| `web2` | `/kitchensink-ear-web2` | `/rest2` | `/members` | POST — register |

The two modules share the same EJB module (same `MemberRegistration` and `MemberRepository` beans, same `AA_Registrant` table).

---

## Business Logic Embedded in JSF Managed Beans (migration flags)

The following logic lives in UI beans and must be preserved or explicitly re-homed during migration:

### MemberController (web, `@Model` request-scoped)
- `register()`: delegates to `MemberRegistration.register()`; handles success/failure by adding JSF `FacesMessage`. No domain logic embedded beyond delegation.
- `initNewMember()` (`@PostConstruct`): resets the form by instantiating a new `Member`. **Also creates a diagnostic test Member (`email="test@mail.gr"`), serializes it with Gson, and logs it.** This is not a functional business rule, but the Gson dependency (`com.google.gson`) must be accounted for in the migration dependency manifest.

### MemberControllerSecond (web2, `@Model` request-scoped)
- Same registration delegation as `MemberController`.
- `initNewMember()` (`@PostConstruct`): resets the form AND **reads the DeltaSpike config property `config.key`** via `ConfigResolver.getPropertyValue("config.key", "Default value")`. Configured value: `"configuration value from deltaspike file"` (from `web2/src/main/resources/META-INF/apache-deltaspike.properties`). Currently only used for logging, but the config-reading pattern must be migrated to Spring's `@Value` / `Environment`. **⚠ Flag: config injection logic lives in the UI bean.**

---

## Persistence

- Persistence unit name: `primary`
- JTA datasource JNDI: `jdbc/SSA`
- Schema management: `hibernate.hbm2ddl.auto=create-drop` (schema created fresh on every deployment, dropped on undeploy — in-memory H2 database)
- All transactions are container-managed (CMT via `@Stateless` EJB); no explicit `@Transactional` on service.

---

## CDI Event Flow

```
MemberRegistration.register(member)
  → em.persist(member)          // writes to DB inside CMT
  → Event<Member>.fire(member)  // CDI event
      → MemberListProducer.onMemberListChanged()  // Reception.IF_EXISTS
          → MemberRepository.findAllOrderedByName()  // refreshes in-request list
```

`Reception.IF_EXISTS` means the observer only fires if the `MemberListProducer` bean has already been instantiated in the current request context. REST-only calls that never touch the JSF list producer will not trigger the observer — this is expected.

---

## WebLogic-specific Configuration (migration scope)

| Artifact | Setting | Impact on migration |
|---|---|---|
| `weblogic-application.xml` | `<library-ref>CommonLibsWarForEar</library-ref>` | Shared library — no Spring Boot equivalent; libraries must be bundled |
| `weblogic-application.xml` | Session `persistent-store-type=memory`, `sharing-enabled=true` | Sessions stored in memory; session sharing across WARs in EAR — Spring Boot single WAR has no EAR session sharing |
| `weblogic-application.xml` | `prefer-application-packages: org.slf4j.*, log4j.*` | Prevents server classloader conflicts; irrelevant in Spring Boot fat-jar |
| `weblogic.xml` (web) | `context-root=kitchensink-ear-web` | Target context path for web1 |
| `weblogic.xml` (web2) | `context-root=kitchensink-ear-web2` | Target context path for web2 |
| Session timeout | 30 minutes (from `web.xml`) | Must be replicated in Spring Boot (`server.servlet.session.timeout=30m`) |
