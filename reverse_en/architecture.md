# As-Is Architecture — kitchensink-ear

Generated: 2026-09-01 | Source: `original_app/` (read-only legacy)

---

## 1. Build & Packaging

| Attribute | Value |
|---|---|
| Build tool | Maven 3, multi-module POM |
| Java version | 8 (compiler source/target) |
| Runtime target | WildFly 11 / JBoss EAP 7 (WebLogic descriptors also present) |
| Java EE spec | Java EE 7 (`jboss-javaee-7.0` BOM) |
| Artifact | EAR (`kitchensink-ear-11.0.0-SNAPSHOT.ear`) |

### Module hierarchy

```
kitchensink-ear (pom)
├── ejb      → kitchensink-ear-ejb.jar   (EJB 3.2 module)
├── web      → kitchensink-ear-web.war   (primary WAR, context /kitchensink-ear-web)
├── web2     → kitchensink-ear-web2.war  (secondary WAR, context /kitchensink-ear-web2)
└── ear      → kitchensink-ear.ear       (assembler, no additional classes)
```

---

## 2. Deployment Descriptors

### EAR level (`ear/src/main/application/META-INF/`)

- **`application.xml`** — declares 1 EJB module (`kitchensink-ear-ejb.jar`) and 2 web modules with context roots above.
- **`weblogic-application.xml`** — WebLogic-specific EAR descriptor:
  - References shared library `CommonLibsWarForEar` (v1.0) via `<library-ref>`.
  - Session storage: `<persistent-store-type>memory</persistent-store-type>` with cross-WAR session sharing enabled (`<sharing-enabled>true`).
  - `<prefer-application-packages>` overrides container versions of `org.slf4j.*` and `log4j.*` with the app-bundled ones.

### WAR level (`web/WEB-INF/`, `web2/WEB-INF/`)

- **`web.xml`** (both WARs): Servlet 3.1; session timeout 30 minutes; no explicit servlet or filter mappings (JSF and JAX-RS activate via annotations/CDI).
- **`weblogic.xml`** (both WARs): sets UTF-8 input charset; `web` WAR has `<context-root>kitchensink-ear-web</context-root>`; `web2` WAR additionally has `<show-archived-real-path-enabled>true</show-archived-real-path-enabled>`; JSP debug/keepgenerated enabled in both.
- **`faces-config.xml`** (both): minimal JSF 2.2 configuration; activates JSF servlet; no navigation rules or managed-bean declarations (CDI used instead).
- **`beans.xml`** (ejb, web, web2): CDI 1.1 with `bean-discovery-mode="all"`.

---

## 3. Domain Model & Persistence (EJB module)

### Entity: `Member`

| Field | Column | Constraints |
|---|---|---|
| `id` | PK (auto-generated) | — |
| `name` | `name` | `@NotNull`, `@Size(1–25)`, no-digits pattern |
| `email` | `email` (UNIQUE) | `@NotNull`, `@NotEmpty`, `@Email` |
| `phoneNumber` | `phone_number` | `@NotNull`, `@Size(10–12)`, `@Digits` |

Table name: `AA_Registrant`. Serializable. JAXB-annotated (`@XmlRootElement`).

### Persistence unit: `primary`

- JTA data source: JNDI name `jdbc/SSA`
- Provider: Hibernate (via WildFly BOM)
- `hibernate.hbm2ddl.auto=create-drop` (schema recreated on each deploy)
- `hibernate.show_sql=true`

### CDI producers (EJB module, `Resources.java`)

- `@PersistenceContext(unitName="primary")` exposes `EntityManager` as a CDI bean — allows `@Inject EntityManager em` anywhere.
- SLF4J `Logger` produced per injection point.

---

## 4. Business Logic (EJB module)

### `MemberRegistration` — `@Stateless` EJB

- Persists a `Member` via injected `EntityManager` (Container-Managed Transactions — CMT, default `REQUIRED`).
- Fires a CDI `Event<Member>` after persist, which observers can react to.

### `MemberRepository` — `@ApplicationScoped` CDI bean

- `findById(Long)` — `EntityManager.find`.
- `findByEmail(String)` — JPA Criteria API query.
- `findAllOrderedByName()` — JPA Criteria API, ordered ascending by `name`.

### `MemberListProducer` — `@RequestScoped` CDI bean

- Produces `List<Member>` (EL name `members`) consumed by JSF views.
- `@Observes Member` (CDI event): refreshes the list on every member registration within the same request scope.
- Initialised via `@PostConstruct`.

---

## 5. Web Layer — Primary WAR (`web`)

### JSF front-end

- **`MemberController`** (`@Model` = `@RequestScoped` + `@Named`):
  - Produces `Member newMember` for form binding.
  - `register()` delegates to `MemberRegistration.register()`, reports success/failure via `FacesContext`.
  - `@PostConstruct`: instantiates a test `Member`, serialises it to JSON via **Gson 2.8.6** (for diagnostics only), logs it.

### JAX-RS REST API

- **`JaxRsActivator`** (`@ApplicationPath("/rest")`): activates JAX-RS with no XML.
- **`MemberResourceRESTService`** (`@Path("/members")`, `@RequestScoped`):
  - `GET /rest/members` → JSON list of all members (ordered by name).
  - `GET /rest/members/{id}` → JSON single member; 404 if not found.
  - `POST /rest/members` → validates via `javax.validation.Validator`, calls `MemberRegistration.register()`; returns 200, 400 (violations), or 409 (duplicate email).

### CDI utility (`WebResources.java`)

- Produces `FacesContext` as a `@RequestScoped` CDI bean for injection into controllers.

---

## 6. Web Layer — Secondary WAR (`web2`)

Nearly identical to `web`, with one difference:

- **`MemberControllerSecond`** uses **Apache DeltaSpike 1.8.2** (`ConfigResolver.getPropertyValue("config.key", "Default value")`) in `@PostConstruct` instead of Gson. This is a config-abstraction layer on top of CDI that reads from properties files, JNDI, system properties, etc.
- **`MemberResourceRESTServiceSecond`**: same REST endpoints as `web`; no Gson usage; logger is plain SLF4J.
- `web2` does **not** bundle Gson; it depends on DeltaSpike core API+impl at `provided` scope (expected to be in the shared library or container).

---

## 7. Component Interaction Diagram

```
Browser / REST client
        │
        ▼
┌──────────────────────────────┐   ┌──────────────────────────────┐
│  WAR: kitchensink-ear-web    │   │  WAR: kitchensink-ear-web2   │
│  JSF: MemberController       │   │  JSF: MemberControllerSecond │
│  REST: MemberResourceREST    │   │  REST: MemberResourceRESTSecond│
│  CDI: WebResources (FacesCxt)│   │  CDI: WebResources           │
└────────────┬─────────────────┘   └──────────────┬───────────────┘
             │  @Inject (cross-module via EAR CL)  │
             ▼                                     ▼
┌──────────────────────────────────────────────────────────────────┐
│                    EJB JAR: kitchensink-ear-ejb                  │
│  MemberRegistration (@Stateless, CMT)                            │
│  MemberRepository (@ApplicationScoped, JPA Criteria)            │
│  MemberListProducer (@RequestScoped, CDI observer)               │
│  Resources (CDI producers: EntityManager, Logger)                │
│  Member (@Entity, AA_Registrant table)                           │
└────────────────────────┬─────────────────────────────────────────┘
                         │  JTA / JNDI
                         ▼
                   DataSource: jdbc/SSA
                   (H2 in-memory, managed by container)
```

CDI event flow:
- `MemberRegistration.register()` fires `Event<Member>`.
- `MemberListProducer.onMemberListChanged()` observes it (within same request scope) and reloads the member list for JSF rendering.

---

## 8. Container / Runtime Assumptions

| Concern | Container-provided |
|---|---|
| JTA transaction management | WildFly / WebLogic JTA |
| DataSource | JNDI `jdbc/SSA` (must be pre-configured) |
| CDI container | Built-in Weld |
| JSF runtime | Built-in Mojarra / MyFaces |
| JAX-RS runtime | RESTEasy (WildFly) |
| EJB container | Built-in |
| Shared library | `CommonLibsWarForEar` 1.0 (WebLogic library-ref — bundles Gson 2.8.6, DeltaSpike 1.8.2) |
| Session management | In-memory, cross-WAR sharing via WebLogic session sharing |
| Classloader isolation | `prefer-application-packages` for SLF4J / Log4j |
| Security | **None configured** — no `<security-constraint>`, no `<login-config>`, no roles |

---

## 9. Third-Party Libraries (application-owned)

| Library | Version | Used in | Purpose |
|---|---|---|---|
| Gson | 2.8.6 | `web` WAR | JSON serialisation in `MemberController` (diagnostic only) |
| Apache DeltaSpike | 1.8.2 (core-api + core-impl) | `web2` WAR | Config resolution (`ConfigResolver`) in `MemberControllerSecond` |
| SLF4J API | 1.7.21 | ejb, web | Logging facade (impl provided by container) |
| Hibernate Validator | managed by BOM | ejb, web | Bean Validation implementation |
| Arquillian (junit + servlet) | managed by BOM | ejb (test) | Integration testing in-container |

Spring Framework 4.3.9 is declared in the root POM `<properties>` but **never used** — no dependency on any `spring-*` artifact appears in any module POM.

---

## 10. Testing

- Integration test: `MemberRegistrationIT` in `ejb/src/test/` — Arquillian-based, deploys a micro-archive, exercises `MemberRegistration.register()` against an in-memory H2 datasource defined in `test-ds.xml`.
- No unit tests; no mocking framework.

---

## 11. Migration Complexity Summary

| Area | Legacy | Complexity | Notes |
|---|---|---|---|
| Namespace | `javax.*` | **High** | All imports → `jakarta.*` |
| EJB → Spring service | `@Stateless` + CMT | **Medium** | Replace with `@Service` + `@Transactional` |
| JPA | Hibernate 2.1 dialect | **Low** | JPA mapping largely portable; rename persistence unit |
| CDI events | `Event<Member>` / `@Observes` | **Medium** | Replace with Spring `ApplicationEvent` or `@EventListener` |
| CDI producers | `@Produces` EntityManager, Logger, FacesContext | **Medium** | Replace with Spring `@Bean` / direct injection |
| JSF | JSF 2.2 + `@Model` | **High** | No JSF in Spring Boot — rewrite UI as Thymeleaf or REST+SPA |
| JAX-RS | RESTEasy + `@ApplicationPath` | **Medium** | Migrate to Spring MVC `@RestController` |
| DeltaSpike config | `ConfigResolver` | **Low** | Replace with `@Value` / `Environment` |
| Shared library (WebLogic) | `CommonLibsWarForEar` | **Medium** | Inline Gson + DeltaSpike replacement as Maven deps |
| WebLogic descriptors | `weblogic-application.xml`, `weblogic.xml` | **High** | Entirely container-specific — discard, configure in `application.properties` |
| DataSource / JNDI | `jdbc/SSA` (JNDI) | **Medium** | Replace with Spring Boot datasource auto-config |
| Session sharing | WebLogic in-memory cross-WAR | **Medium** | Single Spring Boot app eliminates cross-WAR concern |
| Security | None | **Low** | No legacy config to migrate; add Spring Security from scratch if required |
| Tests | Arquillian in-container | **High** | Rewrite as Spring Boot slice tests or MockMvc |
