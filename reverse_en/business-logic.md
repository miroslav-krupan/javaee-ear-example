# Business Logic — kitchensink-ear

> Analyst: business-logic-analyst · Loop: loop-1 · Issue: #5 · Date: 2026-08-31
> Facts about current behaviour only — no target design, no code changes.

---

## Domain Entity

### Member (`ejb/.../model/Member.java`, table `AA_Registrant`)

`Member` is the only domain object. It represents a person who has registered in the system.

| Field | Java type | DB column | Notes |
|---|---|---|---|
| `id` | `Long` | `id` (PK, auto-generated) | Surrogate key; assigned by JPA on persist |
| `name` | `String` | `name` | Human-readable name of the registrant |
| `email` | `String` | `email` (unique) | Contact email; acts as the natural key |
| `phoneNumber` | `String` | `phone_number` | Contact phone stored as a digit string |

The entity is annotated `@XmlRootElement`, making it directly serialisable to JSON and XML by JAX-RS.

---

## Business Rules and Validations

All rules are expressed as Bean Validation annotations on `Member` and are enforced before any persistence attempt.

### Name
- **Required** (`@NotNull`)
- **Length**: 1–25 characters (`@Size(min=1, max=25)`)
- **Format**: must contain no digit characters (`@Pattern(regexp="[^0-9]*", message="Must not contain numbers")`)

### Email
- **Required** and non-blank (`@NotNull`, `@NotEmpty` from Hibernate Validator)
- **Format**: must be a syntactically valid email address (`@Email` from Hibernate Validator — note: legacy annotation, not `jakarta.validation`)
- **Uniqueness** (two-level enforcement):
  1. **Application pre-check**: before calling `register()`, the REST service calls `emailAlreadyExists()` which queries the DB and throws `ValidationException("Unique Email Violation")` if a row is found. This allows the caller to distinguish "email taken" from generic constraint violations.
  2. **Database constraint**: `@UniqueConstraint(columnNames="email")` on `AA_Registrant` — last line of defence.

### Phone Number
- **Required** (`@NotNull`)
- **Length**: 10–12 characters (`@Size(min=10, max=12)`)
- **Format**: numeric digits only, no fractional part (`@Digits(fraction=0, integer=12)`)

---

## Core Use-Cases / Flows

### 1. Register a Member

**Inputs**: `name`, `email`, `phoneNumber`

**Processing**:
1. Run Bean Validation on all three fields.
2. Check email uniqueness (application-level query).
3. Persist the `Member` entity (JPA assigns `id`).
4. Fire a CDI `Event<Member>` to notify observers.

**Outcomes**:

| Result | Condition |
|---|---|
| Success | All validations pass; member is persisted and `id` is assigned |
| Field validation failure | One or more Bean Validation constraints violated; no persist |
| Duplicate email | Email already registered; no persist |
| Other error | Unexpected exception; transaction rolls back |

**Transaction boundary**: `MemberRegistration` is a `@Stateless` EJB. Every invocation of `register()` runs in a JTA-managed transaction (`REQUIRED`). Any exception causes rollback — the member is not persisted.

**Entry points**:

| Surface | Class | Validation approach |
|---|---|---|
| JSF form, `web` module | `MemberController.register()` | No explicit pre-validation; relies on JTA rollback to surface errors |
| JSF form, `web2` module | `MemberControllerSecond.register()` | Same — no explicit pre-validation |
| REST POST, both modules | `MemberResourceRESTService` / `MemberResourceRESTServiceSecond` | Explicit `validateMember()` call before `register()` |

### 2. List All Members

**Inputs**: none

**Output**: all `Member` records sorted ascending by `name`

**Entry points**:
- **JSF view** (both modules): EL variable `#{members}` produced by `MemberListProducer` — populated at request scope `@PostConstruct` and refreshed after each successful registration via CDI event.
- **REST GET** (both modules): `GET /rest/members` → JSON array

### 3. Look Up a Member by ID

**Inputs**: numeric `id` path parameter (regex `[0-9][0-9]*` — one or more digits)

**Output**: matching `Member` as JSON, or HTTP 404 if not found

**Entry point**: `GET /rest/members/{id}` (both web modules)

---

## CDI Event-Driven List Refresh

`MemberRegistration.register()` fires `javax.enterprise.event.Event<Member>` after `em.persist()`. `MemberListProducer` declares:

```java
public void onMemberListChanged(
    @Observes(notifyObserver = Reception.IF_EXISTS) final Member member) {
    retrieveAllMembersOrderedByName();
}
```

`Reception.IF_EXISTS` means the observer only fires if `MemberListProducer` is already instantiated in the current request scope. When it fires, it re-queries all members. This decouples the registration service from the list display without a redirect.

---

## REST API — Error Contract

Both `MemberResourceRESTService` and `MemberResourceRESTServiceSecond` implement identical error handling:

| Condition | HTTP Status | Response body |
|---|---|---|
| Success | `200 OK` | empty body |
| Bean Validation violation(s) | `400 Bad Request` | `{"fieldName": "violation message", …}` |
| Duplicate email | `409 Conflict` | `{"email": "Email taken"}` |
| Other exception | `400 Bad Request` | `{"error": "<exception message>"}` |

---

## Logic Embedded in UI Beans — Migration Risk

The following non-trivial behaviours live in JSF backing beans rather than the service layer. They must be preserved when the UI is replaced.

### `MemberController` (`web` module)
- **`initNewMember()` (PostConstruct)**: creates a throwaway `Member`, serialises it to JSON using **Gson**, and logs it. No business purpose — development/debug instrumentation. Establishes Gson as a runtime dependency of this module.

### `MemberControllerSecond` (`web2` module)
- **`initNewMember()` (PostConstruct)**: reads the configuration property `config.key` (fallback `"Default value"`) via **DeltaSpike `ConfigResolver`** and logs it. This is the only point in the codebase where DeltaSpike configuration is consumed. The property source (properties file, JNDI, system property) is not visible in code. On migration, a replacement (`@Value("${config.key:Default value}")` + `application.properties`) must provide the same key.

### Both controllers — error unwrapping
`getRootErrorMessage()` walks the full `Throwable.getCause()` chain and returns the deepest `localizedMessage`. This is displayed directly to the user as a JSF error message. Any replacement UI must replicate this unwrapping or provide equivalent user-facing error messaging.

---

## Duplicate Web Modules (web vs web2)

Both WARs expose the same member registration feature at different context roots and share the EJB backend:

| Aspect | `web` (`/kitchensink-ear-web`) | `web2` (`/kitchensink-ear-web2`) |
|---|---|---|
| JSF controller | `MemberController` (Gson debug in init) | `MemberControllerSecond` (DeltaSpike config read in init) |
| REST service | `MemberResourceRESTService` | `MemberResourceRESTServiceSecond` |
| REST behaviour | Identical | Identical |
| Core business logic | Identical | Identical |
| Shared backend | Both call the same `MemberRegistration` EJB and `MemberRepository` from `ejb.jar` |

There is no data isolation between the two WARs — both read/write the same `AA_Registrant` table.

---

## Persistence Configuration

| Property | Value |
|---|---|
| JNDI datasource | `jdbc/SSA` (server-configured, not in app) |
| DDL strategy | `hbm2ddl.auto=create-drop` — schema created on deploy, dropped on undeploy |
| JPA provider | Hibernate (via WildFly BOM) |

`create-drop` is a development/demo setting. The migration must choose a production-grade schema management strategy (e.g. Flyway or Liquibase).
