# As-Is Architecture — kitchensink-ear

> Reverse-engineered from `original_app/` — read-only source, never modified.
> Target: Java 21 / Spring Boot 3.4 / Maven 3.9 / Jakarta EE namespace.

---

## 1. Identity & Build

| Attribute | Value |
|---|---|
| Root artifact | `org.wildfly.quickstarts:kitchensink-ear:11.0.0-SNAPSHOT` |
| Java source/target | 8 |
| Build tool | Maven multi-module (parent POM + 4 modules) |
| Final packaging | EAR |
| App server | WildFly / JBoss EAP — but has WebLogic deployment descriptors (`weblogic-application.xml`, `weblogic.xml`) indicating dual-server adaptation |

---

## 2. Module Structure

```
kitchensink-ear (EAR)
├── ejb/         → kitchensink-ear-ejb.jar    (domain + persistence + service)
├── web/         → kitchensink-ear-web.war     (primary UI + REST)
├── web2/        → kitchensink-ear-web2.war    (mirror of web, with DeltaSpike config)
└── ear/         → kitchensink-ear.ear         (assembly: packages the three above)
```

### EAR Descriptors

- **`application.xml`** (J2EE 1.3 DTD — old format): declares ejb jar + two WARs with context roots `/kitchensink-ear-web` and `/kitchensink-ear-web2`.
- **`weblogic-application.xml`**: references shared library `CommonLibsWarForEar` (v1.0), sets session persistence to `memory`, enables cross-module session sharing (`sharing-enabled=true`), and sets `prefer-application-packages` for `org.slf4j.*` and `log4j.*` to override WebLogic's built-in versions.

---

## 3. EJB Module — Domain, Persistence, Service

### 3.1 Persistence

| Item | Detail |
|---|---|
| JPA version | 2.1 |
| Persistence unit | `primary` |
| JTA datasource | `jdbc/SSA` (JNDI; deployed via `ear/META-INF/kitchensink-ear-quickstart-ds.xml`) |
| DDL strategy | `hibernate.hbm2ddl.auto = create-drop` (schema recreated on each deploy) |
| ORM | Hibernate (provided by app server) |

**Entity: `Member`** — table `AA_Registrant`, unique constraint on `email`.

| Field | Column | Type | Constraints |
|---|---|---|---|
| `id` | `id` | `Long` | `@Id @GeneratedValue` (auto) |
| `name` | `name` | `String` | `@NotNull @Size(1–25)` `@Pattern([^0-9]*)` |
| `email` | `email` | `String` | `@NotNull @NotEmpty @Email` |
| `phoneNumber` | `phone_number` | `String` | `@NotNull @Size(10–12) @Digits` |

Also annotated `@XmlRootElement` for JAXB serialization (JAX-RS JSON via container marshaller).

### 3.2 Data Access Layer

**`MemberRepository`** (`@ApplicationScoped` CDI bean):
- JPA Criteria API (no JPQL strings, no named queries).
- `findById(Long)`, `findByEmail(String)` (unique result), `findAllOrderedByName()`.
- `EntityManager` injected via CDI (produced by `Resources`).

**`MemberListProducer`** (`@RequestScoped` CDI bean):
- Produces `@Named List<Member> members` — consumed by JSF EL.
- Observes CDI `Member` events (`@Observes Reception.IF_EXISTS`) to refresh list after registration.
- `@PostConstruct` loads initial list.

### 3.3 Service Layer

**`MemberRegistration`** (`@Stateless` EJB):
- Container-Managed Transactions (CMT) — `REQUIRED` by default.
- `register(Member)`: calls `em.persist(member)` then fires `Event<Member>` via CDI event bus.
- Single business method; no `@TransactionAttribute` override — pure CMT default.

### 3.4 CDI Producers (ejb module)

**`Resources`**:
- `@Produces @PersistenceContext(unitName="primary") EntityManager em` — makes EM injectable via `@Inject` across EJB and WAR modules sharing the EAR classloader.
- `@Produces Logger produceLog(InjectionPoint)` — SLF4J logger per class via `LoggerFactory.getLogger(...)`.

---

## 4. Web Module (web WAR) — Primary UI + REST

**Context root:** `/kitchensink-ear-web`

### 4.1 JSF Layer

- JSF 2.2 Facelets (`.xhtml`).
- Views: `index.xhtml` (registration form + member list), `WEB-INF/templates/default.xhtml` (master layout).
- `faces-config.xml`: present (minimal — no navigation rules or managed beans declared; CDI-only).
- **`MemberController`** (`@Model` = `@RequestScoped + @Named`):
  - Injects `MemberRegistration` service and `FacesContext` (from `WebResources`).
  - `register()`: delegates to `memberRegistration.register(newMember)`, adds JSF `FacesMessage`.
  - `@PostConstruct initNewMember()`: creates a blank `Member`; also serializes a test `Member` to JSON via **Gson** and logs it (debug artifact, not production logic).
  - `@Produces @Named Member getNewMember()` — binds form fields in EL.

**`WebResources`**: CDI producer for `FacesContext.getCurrentInstance()` (`@RequestScoped`).

### 4.2 JAX-RS Layer

- Activated via `JaxRsActivator extends Application` with `@ApplicationPath("/rest")`.
- **REST base path:** `/kitchensink-ear-web/rest`
- **`MemberResourceRESTService`** (`@RequestScoped`, `@Path("/members")`):

| Method | Path | Produces | Consumes | Behaviour |
|---|---|---|---|---|
| `GET` | `/members` | `application/json` | — | Returns all members ordered by name |
| `GET` | `/members/{id}` | `application/json` | — | Lookup by id; 404 if not found |
| `POST` | `/members` | `application/json` | `application/json` | Bean Validation + duplicate email check + `registration.register()` |

- Bean Validation via injected `javax.validation.Validator` (not relying on container auto-validation).
- Returns structured error maps for constraint violations (400) and duplicate email (409).

### 4.3 Web Descriptors

- **`web.xml`** (Servlet 3.1): session timeout 30 minutes. No servlet/filter/security declarations.
- **`weblogic.xml`**: UTF-8 charset, JSP debug mode on, context root `/kitchensink-ear-web`. Container-descriptor with prefer-packages commented out (delegated to EAR level).

---

## 5. Web2 Module (web2 WAR) — Mirror with DeltaSpike Config

**Context root:** `/kitchensink-ear-web2`

Near-identical to the `web` module with one key difference:

**`MemberControllerSecond`** uses **DeltaSpike 1.8.2** `ConfigResolver`:
```java
String configValue = ConfigResolver.getPropertyValue("config.key", "Default value");
```
This resolves properties from DeltaSpike's config sources (properties files, system properties, environment variables, JNDI, etc.) at request init time — logged but not further used in business logic. This is the only divergence from `MemberController`.

`MemberResourceRESTServiceSecond`: functionally identical to `MemberResourceRESTService`.

**`weblogic.xml`**: `<show-archived-real-path-enabled>true</show-archived-real-path-enabled>` active (in `web` it is commented out). Session-sharing relies on the EAR-level `weblogic-application.xml`.

---

## 6. Component Call Graph

```
HTTP (JSF form)
  └─ MemberController / MemberControllerSecond (@Model, web/web2)
       └─ MemberRegistration (@Stateless EJB, ejb.jar)   [CMT begins here]
            ├─ EntityManager.persist(Member)              → jdbc/SSA
            └─ Event<Member>.fire()
                 └─ MemberListProducer.onMemberListChanged()
                      └─ MemberRepository.findAllOrderedByName()
                           └─ EntityManager (Criteria API) → jdbc/SSA

HTTP (REST)
  └─ MemberResourceRESTService / MemberResourceRESTServiceSecond (@RequestScoped, web/web2)
       ├─ Validator.validate(Member)                      [Bean Validation]
       ├─ MemberRepository.findByEmail() / findById()     → jdbc/SSA
       └─ MemberRegistration.register()                   → (same path as above)
```

---

## 7. Cross-Cutting Concerns

### Transactions
- JTA, container-managed (CMT via `@Stateless`).
- No `@TransactionAttribute` overrides — all business methods use `REQUIRED`.
- Datasource `jdbc/SSA` participates in JTA.

### Datasource
- Production: `jdbc/SSA` — JNDI name; actual driver/URL defined in `kitchensink-ear-quickstart-ds.xml` (H2 or equivalent for dev).
- Test: `java:jboss/datasources/KitchensinkEarQuickstartTestDS` via `test-ds.xml` (H2 in-memory, `create-drop`).

### Configuration
- `web` module: no externalized config beyond the datasource JNDI name.
- `web2` module: DeltaSpike `ConfigResolver` for key `config.key` with default `"Default value"` — reads from DeltaSpike's layered config sources.

### Logging
- SLF4J API (`1.7.21`), bound via `log4j.xml` in both WARs' `WEB-INF/classes/`.
- Logger instances produced by CDI (`Resources.produceLog`).

### Sessions
- HTTP sessions: 30-minute timeout (web.xml).
- WebLogic EAR descriptor enables cross-module session sharing (`sharing-enabled=true`) and in-memory persistence.

### Security
- **None configured.** No `@RolesAllowed`, no `web.xml` security constraints, no `weblogic.xml` security policies.

### Shared Library (WebLogic-specific)
- `CommonLibsWarForEar` referenced in `weblogic-application.xml` — an external WebLogic shared library (present as `CommonLibsEar.zip` at the repo root). Contains logging and common dependencies pre-loaded at the EAR classloader level.

---

## 8. Third-Party Dependencies (non-Java-EE)

| Library | Version | Used by | Purpose |
|---|---|---|---|
| Gson | 2.8.6 | `web` (MemberController) | JSON debug serialization in @PostConstruct (log only) |
| DeltaSpike core | 1.8.2 | `web2` (MemberControllerSecond) | External config resolution via `ConfigResolver` |
| SLF4J API | 1.7.21 | ejb, web, web2 | Logging facade |
| H2 | 1.4.193 | test only | In-memory DB for Arquillian tests |
| JUnit | 4.12 | test only | Unit/integration test runner |
| Spring Framework | 4.3.9.RELEASE | declared in root POM | **Not used in any source file** — dead dependency |
| Apache HttpClient | 4.5.3 | declared in root POM | **Not found in any source file** — declared but unused |
| commons-io | 2.5 | declared in root POM | Not found in sources |
| commons-lang3 | 3.5 | declared in root POM | Not found in sources |

---

## 9. Test Infrastructure

- **Arquillian** integration tests (`ejb` module, `src/test/java`).
- Container: JBoss/WildFly managed (JBOSS_HOME env var).
- `MemberRegistrationIT`: deploys a micro-WAR (Member + MemberRegistration + Resources) via ShrinkWrap, injects `MemberRegistration`, calls `register()`, asserts `id` is assigned.
- H2 in-memory datasource for test isolation.

---

## 10. Migration Complexity Summary

| Area | Complexity | Notes |
|---|---|---|
| EAR → single Spring Boot JAR | **Medium** | Three modules collapse; EAR classloader sharing ends |
| `@Stateless` EJB → Spring `@Service` | **Low** | One service class, simple CMT → `@Transactional` |
| JPA / Hibernate → Spring Data JPA | **Low** | Criteria API queries → JpaRepository or QueryDSL |
| JAX-RS → Spring MVC REST | **Low** | Two near-identical REST resources; straightforward mapping |
| JSF → (TBD) | **High** | JSF Facelets views need replacement (Thymeleaf / Vaadin / REST+SPA) |
| CDI @Produces → Spring @Bean | **Low** | EntityManager producer → auto-wired; Logger producer → SLF4J direct |
| DeltaSpike ConfigResolver → Spring Environment | **Low** | `@Value` or `Environment.getProperty()` |
| WebLogic descriptors | **Low** | Deleted; replaced by Spring Boot embedded Tomcat defaults |
| Session sharing (cross-WAR) | **N/A** | Single app — no longer relevant |
| Javax → Jakarta namespace | **Medium** | All `javax.*` imports must be renamed to `jakarta.*` |
| Datasource (JNDI `jdbc/SSA`) | **Low** | Replace with Spring Boot `spring.datasource.*` properties |
| CommonLibsEar shared library | **Medium** | Classify contents; add as direct Maven dependencies or drop |
| Spring 4.x declared but unused | **None** | Remove from POM |
| Arquillian tests → JUnit 5 + Spring Test | **Medium** | Container-based tests become Spring context tests |
