# Target Architecture — kitchensink-ear → Spring Boot 3.4 / Java 21

> Decision document. No code. Produced by the target-architecture agent, loop-1, issue #5.
> Every specialist self-scopes from the section(s) that cover their concern.

---

## 1. Module Collapse — EAR → Single Spring Boot JAR

The 3-Maven-module EAR (`ejb.jar`, `web.war`, `web2.war`) collapses into **one single-module Spring Boot executable JAR**.

**Rationale:**
- `web` and `web2` are functionally identical (same domain, same REST contract). The only behavioural differences are a Gson debug snippet in `web` (dropped — confirmed dead code) and a DeltaSpike config read in `web2` (mapped to `@Value`). No runtime benefit justifies two context roots.
- Cross-WAR session sharing (`sharing-enabled=true`) is a WebLogic-only mechanism; collapsing the WARs eliminates the requirement entirely.
- The EJB module's sole purpose is CMT transaction boundary, which becomes a Spring `@Service` + `@Transactional` bean — no separate Maven module needed.

**Target Maven layout:**

```
kitchensink/
├── pom.xml                                     ← spring-boot-starter-parent:3.4.x, Java 21
└── src/
    ├── main/
    │   ├── java/com/example/kitchensink/
    │   │   ├── KitchensinkApplication.java     ← @SpringBootApplication
    │   │   ├── domain/
    │   │   │   └── Member.java                 ← @Entity (jakarta.persistence)
    │   │   ├── repository/
    │   │   │   └── MemberRepository.java       ← Spring Data JPA
    │   │   ├── service/
    │   │   │   ├── MemberRegistrationService.java
    │   │   │   └── MemberRegisteredEvent.java  ← ApplicationEvent
    │   │   ├── web/
    │   │   │   ├── MemberController.java       ← @Controller (Thymeleaf)
    │   │   │   └── MemberListModel.java        ← @RequestScope (replaces MemberListProducer)
    │   │   └── api/
    │   │       └── MemberRestController.java   ← @RestController /rest/members
    │   └── resources/
    │       ├── application.properties
    │       ├── templates/
    │       │   └── index.html                  ← Thymeleaf template (replaces Facelets)
    │       └── db/migration/
    │           └── V1__create_registrant.sql   ← Flyway baseline
    └── test/
        └── java/com/example/kitchensink/       ← Spring Boot test slices (@DataJpaTest, @WebMvcTest)
```

---

## 2. Persistence

**Owner: Persistence specialist**

| Concern | Decision |
|---|---|
| JPA API | `jakarta.persistence.*` via `spring-boot-starter-data-jpa` |
| Repository | Spring Data JPA `JpaRepository<Member, Long>` + custom `findByEmail(String)` returning `Optional<Member>` |
| DDL / schema strategy | **Flyway** (`spring-boot-starter-flyway`). First migration `V1__create_registrant.sql` creates table `AA_Registrant` with all columns and the `email` UNIQUE constraint. `spring.jpa.hibernate.ddl-auto=validate` at runtime — Flyway owns the schema. |
| DB dialect | Not hardcoded. Hibernate auto-detects dialect from JDBC URL. H2 in-memory for tests; production datasource configured via `spring.datasource.*` environment variables. |
| JNDI datasource | Removed. `<jta-data-source>jdbc/SSA</jta-data-source>` in `persistence.xml` replaced by `spring.datasource.url/username/password` in `application.properties`. `persistence.xml` dropped entirely. |
| `findByEmail` safety | Replace `getSingleResult()` (which can throw `NonUniqueResultException`) with Spring Data `Optional<Member> findByEmail(String email)` — eliminates the known production risk noted in the test-behavior analysis. |

---

## 3. Transaction Strategy — Preserve, No Downgrade

The original app uses JTA/CMT on a single `@Stateless` EJB accessing one datasource. There is **no JMS, no XA, no distributed transaction**. A single-resource DB transaction is semantically identical whether managed by a JTA container or Spring's `PlatformTransactionManager`.

**Decision: Spring local `@Transactional`** on `MemberRegistrationService.register()`.

- Spring Boot auto-configures a `JpaTransactionManager` via `spring-boot-starter-data-jpa`. No JTA infrastructure required.
- The semantics are preserved 1:1: `PROPAGATION_REQUIRED` matches the original CMT `REQUIRED` attribute; any unhandled exception rolls back the transaction exactly as before.
- The JTA transaction manager, `persistence.xml` `transaction-type="JTA"`, and the JNDI datasource are removed.
- **No XA. No JTA container. Faithful preservation of original behaviour.**

---

## 4. Business Logic

**Owner: Business Logic specialist**

| Old (JEE 7) | New (Spring Boot 3.4) |
|---|---|
| `@Stateless MemberRegistration` EJB | `@Service @Transactional MemberRegistrationService` |
| CDI `Event<Member>` fired after `em.persist` | `ApplicationEventPublisher.publishEvent(new MemberRegisteredEvent(member))` |
| `@Observes(IF_EXISTS) Member` on `MemberListProducer` | `@EventListener` on `@RequestScope` bean `MemberListModel.onMemberRegistered()` — Spring dispatches to in-scope beans only |
| `MemberController.initNewMember()` Gson snippet | **Drop** — confirmed dead test code by test-behavior analyst (§2.5). Gson dependency removed entirely. |
| `MemberControllerSecond` DeltaSpike `ConfigResolver.getPropertyValue("config.key","Default value")` | `@Value("${config.key:Default value}")` injected into the replacement controller. Property declared in `application.properties`; overridable by env var `CONFIG_KEY`. |
| `getRootErrorMessage(Throwable)` root-cause unwrapper | Preserve as utility method; bind result to Thymeleaf model attribute for user-facing error display. |
| Bean Validation namespace | `javax.validation.*` → `jakarta.validation.*` throughout. `org.hibernate.validator.constraints.Email` → `jakarta.validation.constraints.Email`. `org.hibernate.validator.constraints.NotEmpty` → `jakarta.validation.constraints.NotBlank`. |

The REST error contract (HTTP 400 / 409 / 404, field-keyed error map) is preserved exactly — see §5.

---

## 5. Web / API Layer

**Owner: Web / Sync Comm specialist**

### 5.1 REST API — Spring MVC `@RestController`

JAX-RS is replaced by Spring MVC. **Jersey is not used** — Spring MVC is fully BOM-managed and native to Spring Boot.

| Old (JAX-RS) | New (Spring MVC) |
|---|---|
| `@ApplicationPath("/rest")` | `@RestController @RequestMapping("/rest")` |
| `@Path("/members") @GET` | `@GetMapping("/members")` → 200 JSON array |
| `@Path("/members/{id:[0-9][0-9]*}") @GET` | `@GetMapping("/members/{id}")` → 200 JSON or 404 |
| `@POST /members` | `@PostMapping("/members")` with `@Valid @RequestBody` |
| JSON via `@XmlRootElement` (JAX-RS) | Jackson (`spring-boot-starter-web`); `@XmlRootElement` dropped |
| Manual `Validator.validate()` + `ConstraintViolationException` | `@Valid` + `@ExceptionHandler(MethodArgumentNotValidException)` → 400 `{field: message}` map |
| `ValidationException` (duplicate email) | Explicit pre-check + `ResponseEntity` with status 409 `{"email": "Email taken"}` |
| `Exception` fallback → 400 `{"error": message}` | `@ExceptionHandler(Exception.class)` → 400 |

Both `MemberResourceRESTService` and `MemberResourceRESTServiceSecond` collapse to **one** `MemberRestController`. HTTP contract is preserved exactly.

Context path: `server.servlet.context-path=/` (default). REST base URL: `http://<host>/rest/members`.

### 5.2 Frontend — Thymeleaf

JSF (Facelets, `faces-config.xml`, `@Model` backing beans) is replaced by **Thymeleaf** (`spring-boot-starter-thymeleaf`).

| Old (JSF) | New (Thymeleaf) |
|---|---|
| `MemberController` + `MemberControllerSecond` (`@Model`) | Single `MemberController` (`@Controller @RequestMapping("/")`) |
| `MemberListProducer` (`@Produces @Named members`) | `MemberListModel` (`@RequestScope`) populated on `@PostConstruct`; refreshed via `@EventListener` |
| `FacesMessage(SEVERITY_INFO)` on success | Flash attribute `successMessage` → Thymeleaf conditional `th:if` |
| `FacesMessage(SEVERITY_ERROR)` + root-cause unwrap | Flash attribute `errorMessage` from `getRootErrorMessage(e)` |
| `WebResources` CDI producer (FacesContext) | Remove — no FacesContext in Thymeleaf |
| `log4j.xml` in `WEB-INF/classes/` | `logback-spring.xml` in `src/main/resources/` |

### 5.3 Session / State

Cross-WAR session sharing is eliminated by the single-app collapse. Standard Spring MVC HTTP sessions are sufficient. Spring Session is out of scope — add only if horizontal scaling or session offloading is required later.

---

## 6. Logging

**Owner: Build & Dependency / Scaffold**

| Old | New |
|---|---|
| SLF4J 1.7.21 + Log4j 1.2.17 | SLF4J 2.x + **Logback** (via `spring-boot-starter-logging`, included transitively by `spring-boot-starter`) |
| `log4j.xml` in each WAR | `logback-spring.xml` in `src/main/resources/` |
| CDI `Resources` `@Produces Logger` | Remove CDI producer. Each class: `private static final Logger log = LoggerFactory.getLogger(ClassName.class)` |
| `org.slf4j.Logger` / `LoggerFactory` in source | **Unchanged** — SLF4J API is stable across 1.x→2.x; no source edits needed |

Log4j 1.2.17 is EOL with known CVEs — removal is a security improvement, not just a cleanup.

---

## 7. Configuration

| Old | New |
|---|---|
| DeltaSpike `ConfigResolver.getPropertyValue("config.key","Default value")` | `@Value("${config.key:Default value}")` in `MemberController` |
| No `*.properties` in repo | `application.properties` created with `config.key=Default value`; env-var override: `CONFIG_KEY` |
| `weblogic.xml` UTF-8 charset override | `server.servlet.encoding.charset=UTF-8` + `server.servlet.encoding.force=true` in `application.properties` |
| WebLogic class-loading overrides for SLF4J / Log4j | Irrelevant — flat classpath in Spring Boot. Use Maven `<exclusion>` if conflicting transitive deps surface. |

---

## 8. Security

No security is declared in the current application (no `web.xml` security constraints, no `@RolesAllowed`, no WebLogic realm configuration). Security was enforced at the WebLogic server layer outside the application boundary.

**Decision: Spring Security is not added in this migration pass.** Scope is functional equivalence only. A follow-up sprint adds `spring-boot-starter-security` when access-control requirements are formally defined.

The Security specialist creates a placeholder `SecurityConfig.java` documenting this decision and permitting all requests for the migration phase.

---

## 9. CommonLibsEar — Complete Elimination

`CommonLibsEar.zip` and the `CommonLibsWarForEar` shared-library mechanism are deleted. Each JAR it provided has an explicit disposition:

| CommonLibsEar JAR | Disposition |
|---|---|
| `deltaspike-core-api-1.8.2.jar` | **Remove** — replaced by `@Value` / Spring `Environment` |
| `deltaspike-core-impl-1.8.2.jar` | **Remove** — CDI extension infrastructure, not applicable to Spring Boot |
| `gson-2.8.6.jar` | **Remove** — only used in dead debug code; not migrated |
| `log4j-1.2.17.jar` | **Remove** — replaced by Logback |
| `slf4j-api-1.7.21.jar` | **Managed by Spring Boot BOM** (SLF4J 2.x) — no explicit declaration needed |
| `slf4j-log4j12-1.7.21.jar` | **Remove** — Log4j 1.x bridge no longer needed |

All WildFly / JBoss BOMs (`wildfly-javaee7-with-tools`, `jboss-javaee-7.0`) are removed. The dead property `version.spring.framework=4.3.9.RELEASE` in root `pom.xml` is deleted.

---

## 10. Dependency Order of Concerns (Specialist Sequencing)

Each tier must produce compilable, independently testable code before the next tier begins.

```
Tier 0 — Build & Dependency (Scaffold)
  Spring Boot parent POM, packaging, empty KitchensinkApplication, application.properties skeleton,
  all compile-scope starter dependencies declared, Flyway + H2 test scope, WildFly BOMs removed.
        │
        ▼
Tier 1 — Persistence specialist
  Member entity (jakarta.persistence.*), MemberRepository (Spring Data JPA),
  Bean Validation constraints (jakarta.validation.*), Flyway V1 migration SQL.
  Compiles independently; tested with @DataJpaTest + H2.
        │
        ▼
Tier 2 — Business Logic specialist
  MemberRegistrationService (@Service @Transactional), MemberRegisteredEvent,
  MemberListModel (@RequestScope @EventListener), @Value config.key wiring, Gson removal.
  Depends on Tier 1 (Member, MemberRepository). Tested with @SpringBootTest (service layer).
        │
        ▼
Tier 3 — Web / Sync Comm specialist  (parallel with Security)
  MemberRestController (@RestController), MemberController (@Controller + Thymeleaf),
  Thymeleaf index.html template, REST error handling.
  Depends on Tier 2 (MemberRegistrationService, MemberListModel).
  Tested with @WebMvcTest.

Tier 3 — Security specialist  (parallel with Web)
  SecurityConfig.java placeholder — permits all, documents rationale.
  No dependencies on Tier 1–2 internals.
```

---

## 11. Compatibility / Out-of-BOM

Libraries **not** managed by the Spring Boot 3.4 BOM, requiring explicit version pinning in the parent POM:

| Artifact | Target version | Notes |
|---|---|---|
| `commons-io:commons-io` | `2.16.1` | Not in Spring Boot BOM; upgrade from 2.5 for CVE remediation |
| `org.apache.commons:commons-lang3` | `3.14.0` | Not in Spring Boot BOM; upgrade from 3.5 |
| `org.apache.httpcomponents.client5:httpclient5` | Managed by Spring Boot 3.4 BOM | HttpClient 4.x (`httpclient:4.5.3`) is EOL — replace groupId/artifactId; version then managed by BOM |
| `commons-logging:commons-logging` | — | Spring Boot BOM routes through `jcl-over-slf4j`; add explicit exclusion if it surfaces transitively |

**Java 21 fit:** Spring Boot 3.4 requires Java 17+ and is fully validated on Java 21. No incompatibilities expected.

**Jakarta EE 10 fit:** Spring Boot 3.4 BOM manages all Jakarta EE 10 API versions. Zero `javax.*` usages must remain in source after migration (13 production source files require full namespace rename).
