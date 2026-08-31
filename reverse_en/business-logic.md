# Business Logic — kitchensink-ear

> Analyst: business-logic-analyst · Loop: loop-1 · Date: 2026-08-31
> Facts about current behaviour only — no target design, no code changes.

---

## Domain Entity

### Member (`model/Member.java`, table `AA_Registrant`)

A **Member** is a person who has registered in the system. It is the only domain object.

| Field | Type | Column | Meaning |
|---|---|---|---|
| `id` | `Long` | `id` (auto-generated PK) | Surrogate identifier assigned on persistence |
| `name` | `String` | `name` | Human-readable name of the registrant |
| `email` | `String` | `email` (unique) | Contact email, serves as natural key |
| `phoneNumber` | `String` | `phone_number` | Contact phone number stored as a string of digits |

---

## Business Rules and Validations

All rules are declared on `Member` via Bean Validation annotations and enforced before persistence.

### Name
- **Required** (`@NotNull`)
- **Length**: 1–25 characters (`@Size(min=1, max=25)`)
- **Format**: must not contain any digit characters (`@Pattern(regexp="[^0-9]*", message="Must not contain numbers")`)

### Email
- **Required** and non-blank (`@NotNull`, `@NotEmpty`)
- **Format**: must be a syntactically valid email address (`@Email` from Hibernate Validator)
- **Uniqueness**: email must not already exist in the system. This is enforced at two levels:
  1. **Application-level pre-check** (`emailAlreadyExists()` in the REST service): queries the DB before `register()` is called and throws `ValidationException("Unique Email Violation")` if a match is found. This separates the "email taken" error from generic constraint violations.
  2. **Database-level constraint**: `@UniqueConstraint(columnNames="email")` on the table.

### Phone Number
- **Required** (`@NotNull`)
- **Length**: 10–12 characters (`@Size(min=10, max=12)`)
- **Format**: digits only, no fractional part (`@Digits(fraction=0, integer=12)`) — stored as a string of numeric characters

---

## Core Use-Cases / Flows

### 1. Register a Member

**Inputs**: name, email, phoneNumber

**Steps**:
1. Validate all fields against the rules above.
2. Check that the email is not already registered.
3. Persist the `Member` entity (auto-generates `id`).
4. Fire a CDI `Event<Member>` to notify observers (triggers member list refresh).

**Outcomes**:

| Result | Condition |
|---|---|
| Success | All validations pass; member is persisted and assigned an `id` |
| Validation failure | One or more field constraints violated; member is NOT persisted |
| Duplicate email | Email already in DB; member is NOT persisted |

**Entry points**:
- **JSF form** (both web modules): `MemberController.register()` / `MemberControllerSecond.register()` — calls `MemberRegistration.register()` directly, relying on JTA transaction rollback to surface errors; no explicit pre-validation step in the controller.
- **REST POST** (both web modules): `POST /rest/members` — explicitly runs `validateMember()` before calling `MemberRegistration.register()`.

**Transaction boundary**: `MemberRegistration` is an `@Stateless` EJB; every call to `register()` runs in a JTA-managed transaction with default `REQUIRED` semantics. A constraint violation or other exception causes the transaction to roll back and the member is not persisted.

### 2. List All Members

**Inputs**: none

**Output**: all registered members, ordered ascending by name

**Entry points**:
- **JSF view** (both web modules): `members` EL variable produced by `MemberListProducer`. List is populated at request scope (`@PostConstruct`) and refreshed after any registration via CDI event observation.
- **REST GET** (both web modules): `GET /rest/members` → JSON array

### 3. Look Up a Member by ID

**Inputs**: numeric `id` (path parameter; regex `[0-9][0-9]*` — must be one or more digits)

**Output**: the matching `Member` as JSON, or HTTP 404 if not found

**Entry point**: `GET /rest/members/{id}` (both web modules)

---

## CDI Event-Driven List Refresh

`MemberRegistration.register()` fires a `javax.enterprise.event.Event<Member>` after persistence. `MemberListProducer` observes this event with `Reception.IF_EXISTS` (only if the observer bean already exists in scope). On observation, it re-queries all members ordered by name. This pattern keeps the JSF member list up-to-date after a registration without a page redirect.

---

## Error Responses (REST)

| Condition | HTTP Status | Response body |
|---|---|---|
| Successful registration | 200 OK | empty |
| Field constraint violation | 400 Bad Request | `{"fieldName": "violation message", …}` |
| Duplicate email | 409 Conflict | `{"email": "Email taken"}` |
| Other exception | 400 Bad Request | `{"error": "<exception message>"}` |

---

## Logic Embedded in UI Beans (Migration Risk)

The following behaviours live in JSF backing beans and must be preserved on migration even though they are not in the service layer.

### `MemberController` (web module)
- **`initNewMember()`**: On request construction, creates a test `Member`, serialises it to JSON with **Gson**, and logs the result. This is development/debug instrumentation — no business purpose. It does establish Gson as a dependency in this module.

### `MemberControllerSecond` (web2 module)
- **`initNewMember()`**: On request construction, reads the configuration property `config.key` (default `"Default value"`) via **DeltaSpike `ConfigResolver`** and logs it. This is the only place DeltaSpike configuration is consumed; the key and its source are not visible in the codebase. On migration, a replacement config mechanism (e.g. `@Value` / `application.properties`) must provide the same property.

### Both controllers — error handling
- On registration failure, both controllers walk the full `Throwable` cause chain and surface the root-cause `localizedMessage` to the user as a JSF error message. This behaviour must be preserved in any replacement UI layer.

---

## Duplicate Web Modules (web vs web2)

The application exposes the same member registration feature through two separate WAR modules deployed at different context roots:

| Aspect | `web` (`/kitchensink-ear-web`) | `web2` (`/kitchensink-ear-web2`) |
|---|---|---|
| JSF controller | `MemberController` (uses Gson in init) | `MemberControllerSecond` (uses DeltaSpike ConfigResolver in init) |
| REST service | `MemberResourceRESTService` | `MemberResourceRESTServiceSecond` |
| REST behaviour | Identical | Identical |
| Business logic | Identical | Identical |
| Shared backend | Both modules share the same `MemberRegistration` EJB and `MemberRepository` CDI bean from the EJB module |

Both modules write to the same `AA_Registrant` table through the shared EJB. There is no isolation between the two web modules at the data level.

---

## Persistence Configuration

- **JNDI datasource**: `jdbc/SSA` (defined in WebLogic, not in the application)
- **DDL strategy**: `hbm2ddl.auto=create-drop` — schema is created on deploy and dropped on undeploy. This is a development/demo setting, not a production pattern.
- **JPA provider**: Hibernate (via JBoss BOM)
