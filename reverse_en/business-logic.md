# Business Logic — kitchensink-ear

## Domain Entities

### Member
The sole domain entity, mapped to table `AA_Registrant` (JPA `create-drop` on startup).

| Field | Type | Constraints | DB Column |
|---|---|---|---|
| `id` | Long | Auto-generated PK | `id` |
| `name` | String | 1–25 chars; no digits (`@Pattern(regexp="[^0-9]*")`); `@NotNull` | `name` |
| `email` | String | Valid email format (`@Email`); `@NotEmpty`; unique in DB | `email` |
| `phoneNumber` | String | 10–12 chars; digits only (`@Digits`); `@NotNull` | `phone_number` |

`Member` is annotated `@XmlRootElement`, enabling JAXB serialisation for REST JSON responses.

---

## Core Use-Cases / Flows

### 1. Register a Member (REST)
**Trigger:** `POST /kitchensink-ear-web/rest/members` (or `/kitchensink-ear-web2/rest2/members`)  
**Actor:** API client  
**Input:** JSON body representing a `Member` (`name`, `email`, `phoneNumber`)

**Flow:**
1. Bean Validation is applied to the submitted `Member` via `javax.validation.Validator`.
2. If any constraint is violated, a `ConstraintViolationException` is thrown; the service returns `HTTP 400` with a JSON map of `{fieldName: violationMessage}`.
3. Email uniqueness is checked by querying `MemberRepository.findByEmail()`. If the email already exists, the service returns `HTTP 409` with `{"email": "Email taken"}`.
4. `MemberRegistration.register(member)` is called:
   - The member is persisted to `AA_Registrant` via `EntityManager.persist()` within a JTA transaction (container-managed, `@Stateless` EJB).
   - A CDI `Event<Member>` is fired after persist, notifying observers.
5. `HTTP 200 OK` is returned on success.
6. On any other exception, `HTTP 400` is returned with `{"error": <root cause message>}`.

**Business rules enforced at this layer:**
- Name must not contain digits.
- Name length: 1–25 characters.
- Email must be syntactically valid and unique across all registrations.
- Phone number must be 10–12 numeric characters.

### 2. Register a Member (JSF Form)
**Trigger:** JSF form submit bound to `MemberController.register()` (web module) or `MemberControllerSecond.register()` (web2 module)  
**Actor:** Browser user  
**Input:** JSF form fields bound to `newMember` (a CDI `@Produces @Named Member`)

**Flow:**
1. `MemberRegistration.register(newMember)` is called (same EJB as the REST path — no separate validation step here; Bean Validation is applied by the JSF lifecycle before the action method fires).
2. On success: a `FacesMessage` with severity `INFO` and text `"Registered!"` is added; `newMember` is reset to a fresh `Member` instance.
3. On exception: `getRootErrorMessage(e)` extracts the deepest cause message; a `FacesMessage` with severity `ERROR` is added.

**Note:** Business rules (field constraints) are identical to the REST path — enforced via the same `@Member` constraint annotations processed by JSF's built-in Bean Validation integration.

### 3. List All Members (REST)
**Trigger:** `GET /kitchensink-ear-web/rest/members` or `GET /kitchensink-ear-web2/rest2/members`  
**Actor:** API client  
**Output:** JSON array of all `Member` records ordered ascending by `name`

Flow: calls `MemberRepository.findAllOrderedByName()` → JPA Criteria API query on `AA_Registrant` ordered by `name ASC`.

### 4. Get Member by ID (REST)
**Trigger:** `GET /kitchensink-ear-web/rest/members/{id}` (id must match `[0-9]+`)  
**Actor:** API client  
**Output:** JSON representation of a single `Member`, or `HTTP 404` if not found.

Flow: calls `MemberRepository.findById(id)` → `EntityManager.find()`.

---

## Business Rules and Validations

| Rule | Location | Mechanism |
|---|---|---|
| Name: 1–25 chars, letters/spaces only (no digits) | `Member.name` | `@Size(min=1,max=25)` + `@Pattern(regexp="[^0-9]*")` |
| Email: syntactically valid | `Member.email` | `@Email` (Hibernate Validator) |
| Email: non-empty | `Member.email` | `@NotEmpty` |
| Email: globally unique | `MemberResourceRESTService.emailAlreadyExists()` | DB lookup + `HTTP 409` on collision |
| Phone: 10–12 digits | `Member.phoneNumber` | `@Digits` + `@Size(min=10,max=12)` |
| Phone: non-null | `Member.phoneNumber` | `@NotNull` |
| Persistence in a transaction | `MemberRegistration.register()` | `@Stateless` container-managed JTA |

**Important:** The uniqueness check in the REST layer is a read-before-write (not a DB-level lock). A race condition between two concurrent registrations with the same email is possible; the DB unique constraint on `email` provides the final safety net.

---

## Observable Behaviour per Endpoint / Flow

### REST API (`web` module — `/kitchensink-ear-web/rest`)

| Method | Path | Success | Failure |
|---|---|---|---|
| `GET` | `/members` | `200` + JSON array (all members, ordered by name) | — |
| `GET` | `/members/{id}` | `200` + JSON member | `404` if not found |
| `POST` | `/members` | `200` (empty body) | `400` (validation errors map), `409` (duplicate email), `400` (other error) |

### REST API (`web2` module — `/kitchensink-ear-web2/rest2`)

Identical response contract to the `web` module REST API (same paths relative to context root, same status codes and JSON shapes). Minor behavioural difference: the `GET /members` handler in `web2` does not emit the `"INFO: Requesting all users...."` log line. No business logic difference.

### JSF UI (both modules)
- Renders a member registration form bound to `newMember`.
- Displays a `members` list (refreshed after each successful registration via CDI event → `MemberListProducer`).
- Success: inline `"Registered!"` message, form resets.
- Failure: inline error message with root cause text.

---

## Cross-Cutting Concerns with Business Impact

### CDI Event-Driven List Refresh
`MemberRegistration.register()` fires `Event<Member>` after persist. `MemberListProducer` observes this event (`Reception.IF_EXISTS`) and refreshes its `List<Member>` from the DB. This is the mechanism that keeps the JSF member list current after a registration.
**Migration note:** This observer pattern is logic embedded in a CDI bean that must be preserved. In Spring Boot, this can be replaced with `ApplicationEventPublisher` + `@EventListener`, or by direct service-layer cache invalidation.

### Gson Side-Effect in `MemberController` (`web` module)
`@PostConstruct initNewMember()` creates a test `Member(email="test@mail.gr")`, serialises it with `com.google.gson.Gson`, and logs it. This runs on every JSF request. It has no functional effect on the registration flow but introduces a Gson dependency.
**Migration note:** This is likely a development/demo artifact. It should be reviewed — if retained, Gson must be added as a dependency; if removed, no business logic is lost.

### DeltaSpike Config Lookup in `MemberControllerSecond` (`web2` module)
`@PostConstruct initNewMember()` calls `ConfigResolver.getPropertyValue("config.key", "Default value")` and logs the result. This is the only use of Apache DeltaSpike in the application.
**Migration note:** Logic lives in the JSF controller (UI bean). In Spring Boot, `@Value("${config.key:Default value}")` is the equivalent. The key `"config.key"` must be declared in the target `application.properties`.

### Schema Management
`hibernate.hbm2ddl.auto=create-drop` means the schema is recreated on every deploy. The `AA_Registrant` table DDL is never expressed in SQL files.
**Migration note:** In Spring Boot, the team must choose between Flyway/Liquibase migrations or `spring.jpa.hibernate.ddl-auto=create-drop` (only for dev). A DDL script for `AA_Registrant` must be authored for any persistent environment.

### JTA DataSource (`jdbc/SSA`)
The persistence unit binds to the JNDI data source `jdbc/SSA`. This is a container-managed resource.
**Migration note:** Must be replaced by a Spring Boot `DataSource` configured in `application.properties` (driver, URL, credentials).
