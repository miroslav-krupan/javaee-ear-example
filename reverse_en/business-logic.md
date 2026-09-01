# Business Logic — kitchensink-ear

## Domain Entities

### Member
The single domain entity representing a registered member. Stored in table `AA_Registrant`.

| Field | Type | Constraints |
|---|---|---|
| `id` | Long | Auto-generated PK |
| `name` | String | Not null; length 1–25; no digits (regex `[^0-9]*`) |
| `email` | String | Not null; not empty; valid email format; **unique** across all members |
| `phoneNumber` | String | Not null; 10–12 characters; digits only (no letters, no fraction) |

## Core Use-Cases / Flows

### 1. Register a Member

Triggered via two independent surfaces that share the same EJB service:

#### 1a. JSF UI (web and web2)

1. User fills Name, Email, Phone # in the registration form.
2. Browser posts to `MemberController.register()` (web) or `MemberControllerSecond.register()` (web2).
3. The controller calls `MemberRegistration.register(newMember)` — the `@Stateless` EJB persists the member and fires a CDI `Member` event.
4. Bean Validation is applied at JPA persist time (transaction commit); the database unique constraint on `email` is a final safety net.
5. **On success**: `FacesMessage` with severity INFO ("Registered!") is added; form is reset with a fresh `Member`.
6. **On failure**: the root-cause exception message is unwound and displayed as a `FacesMessage` with severity ERROR ("Registration Unsuccessful"); form stays filled.

> **Migration flag — logic in JSF beans:**
> - `MemberController.initNewMember()` instantiates a test `Member`, sets email `"test@mail.gr"`, serialises it to JSON via **Gson**, and logs the result. This is debug-level behaviour but the Gson dependency must be carried over.
> - `MemberControllerSecond.initNewMember()` reads an external config property via **DeltaSpike** `ConfigResolver.getPropertyValue("config.key", "Default value")` and logs it. This config-resolution behaviour is functionally meaningful and must be preserved (e.g. via Spring's `Environment` / `@Value`).

#### 1b. REST API — POST `/members`

1. Client sends `POST /members` with a JSON `Member` body.
2. Bean validation is run eagerly (`Validator.validate()`):
   - Any constraint violations → `400 Bad Request` with body `{fieldName: "violation message", …}`.
3. Email uniqueness is checked explicitly via `MemberRepository.findByEmail()`:
   - Duplicate email → `409 Conflict` with body `{"email": "Email taken"}`.
4. `MemberRegistration.register(member)` persists and fires the CDI event.
5. **On success** → `200 OK` (empty body).
6. Generic exceptions → `400 Bad Request` with body `{"error": "<message>"}`.

### 2. List All Members

- **REST** `GET /members` → returns all members as a JSON array, **ordered alphabetically by name**.
- **JSF** — `MemberListProducer` (request-scoped CDI bean) fetches members ordered by name on `@PostConstruct` and exposes them as the EL variable `#{members}`. The view table shows Id, Name, Email, Phone #, and a link to the REST URL for each member.

### 3. Look Up a Member by ID (REST only)

- `GET /members/{id}` where `{id}` matches `[0-9][0-9]*`.
- Returns the member as JSON, or `404 Not Found` if no such id exists.

## Business Rules and Validations

| Rule | Enforcement point |
|---|---|
| Name must not be empty (min 1 char) | Bean Validation on `Member.name` |
| Name max 25 characters | Bean Validation on `Member.name` |
| Name must not contain digits | Bean Validation regex `[^0-9]*` on `Member.name` |
| Email must be a syntactically valid email address | Bean Validation `@Email` (Hibernate Validator) on `Member.email` |
| Email must be unique | App-level pre-check in REST service (`emailAlreadyExists`) + DB unique constraint on `AA_Registrant.email` |
| Phone number must be 10–12 characters long | Bean Validation `@Size(min=10, max=12)` on `Member.phoneNumber` |
| Phone number must contain digits only | Bean Validation `@Digits(fraction=0, integer=12)` on `Member.phoneNumber` |
| Registration is transactional (all-or-nothing) | Container-managed transaction via `@Stateless` EJB |

## CDI Event Mechanism

After a successful `register()`, `MemberRegistration` fires a `CDI Event<Member>`. `MemberListProducer` observes this event (`Reception.IF_EXISTS`) and re-queries the database to refresh the member list. This is what keeps the JSF view up-to-date immediately after a registration without a page navigation.

## web vs web2 Symmetry

Both WARs expose **identical** REST endpoints and JSF registration forms backed by the **same** shared EJB (`MemberRegistration`) and entity (`Member`). The only behavioural difference is in controller initialisation:

| | web (`MemberController`) | web2 (`MemberControllerSecond`) |
|---|---|---|
| `initNewMember()` side-effect | Gson JSON serialisation of a test member (logged) | DeltaSpike config property `config.key` lookup (logged) |

## Inputs / Outputs Summary

| Surface | Input | Success output | Error output |
|---|---|---|---|
| REST POST `/members` | JSON `{name, email, phoneNumber}` | `200 OK` | `400` (violations/generic), `409` (dup email) |
| REST GET `/members` | — | JSON array ordered by name | — |
| REST GET `/members/{id}` | path param id (digits) | JSON member | `404` |
| JSF Register form | Name, Email, Phone # | Info FacesMessage + form reset | Error FacesMessage with root cause |
| JSF Members table | — | Table of all members ordered by name | Empty-state message "No registered members." |
