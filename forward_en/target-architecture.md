# Target Architecture — kitchensink-ear → Spring Boot 3.4

> **Produced by:** Target Architecture Agent · 2026-09-01 · loopId loop-1 · issue #7  
> **Status:** awaiting-approval  
> **Sources:** reverse_en/architecture.md, reverse_en/business-logic.md, reverse_en/dependencies.md, reverse_en/test-behavior.md

---

## 0. Executive Summary

The four-module Java EE EAR (`ejb.jar` + `web.war` + `web2.war` + `CommonLibsWarForEar` shared library) is collapsed into a **single Spring Boot 3.4 Maven module** targeting Java 21. The two WARs are near-identical; they are merged into one application with consolidated controllers. All WebLogic-specific descriptors and mechanisms are eliminated. The CDI/EJB/JAX-RS/JSF stack is replaced wholesale by Spring IoC / `@Transactional` / Spring MVC REST / Thymeleaf.

---

## 1. Module & Packaging Layout

### 1.1 Source → Target Module Mapping

| As-Is module | Target |
|---|---|
| `ear/` | Deleted — no EAR in Spring Boot |
| `ejb/` | Merged into single Spring Boot module |
| `web/` (context root `/kitchensink-ear-web`) | Merged; URL prefix `/` (primary context) |
| `web2/` (context root `/kitchensink-ear-web2`) | Merged; duplicate controllers deduplicated (see §6) |
| `CommonLibsEar.zip` | **Deleted** — all jars addressed below (§8) |
| `weblogic-application.xml`, `weblogic.xml` (×2) | **Deleted** — no Spring Boot equivalent |
| `beans.xml` (×3) | **Deleted** — CDI replaced by Spring IoC |

### 1.2 Target Maven Structure

```
kitchensink/                      ← single Maven module
├── pom.xml                       ← Spring Boot 3.4 parent BOM, Java 21
└── src/
    ├── main/
    │   ├── java/
    │   │   └── org/example/kitchensink/
    │   │       ├── KitchensinkApplication.java     (main class)
    │   │       ├── model/
    │   │       │   └── Member.java
    │   │       ├── repository/
    │   │       │   └── MemberRepository.java
    │   │       ├── service/
    │   │       │   └── MemberRegistrationService.java
    │   │       ├── web/
    │   │       │   ├── rest/
    │   │       │   │   └── MemberRestController.java
    │   │       │   └── ui/
    │   │       │       └── MemberController.java
    │   │       └── config/
    │   │           └── AppConfig.java              (only if needed)
    │   └── resources/
    │       ├── application.properties
    │       ├── db/migration/                       ← Flyway scripts (V1__init.sql)
    │       └── templates/                          ← Thymeleaf templates
    │           ├── layout/default.html
    │           └── members/index.html
    └── test/
        └── java/org/example/kitchensink/
            ├── model/MemberValidationTest.java
            ├── repository/MemberRepositoryTest.java
            ├── service/MemberRegistrationServiceTest.java
            └── web/rest/MemberRestControllerTest.java
```

### 1.3 Dependency Order of Concerns (specialist sequencing)

Specialists MUST migrate in tier order — each tier compiles cleanly against already-migrated code.

| Tier | Concern | Specialist | Depends on |
|---|---|---|---|
| 0 | Domain model (`Member` entity, constraints) | Persistence | — |
| 1 | Repository (`MemberRepository`) | Persistence | Tier 0 |
| 2 | Service (`MemberRegistrationService`) | Business Logic | Tiers 0, 1 |
| 3a | REST API (`MemberRestController`) | Sync Comm | Tiers 0, 1, 2 |
| 3b | UI (`MemberController` + Thymeleaf templates) | Frontend | Tiers 0, 1, 2 |
| 3c | Tests | Test | Tiers 0–3a/3b |

---

## 2. Persistence

**Target stack:** `spring-boot-starter-data-jpa` (Hibernate 6.x ORM, JPA 3.x).

### 2.1 Entity

`Member` maps to table `AA_Registrant` — table name is preserved (no DDL rename).  
JAXB annotation `@XmlRootElement` is **removed** (JSON-only target; Jackson handles serialisation with no annotation required).

### 2.2 Repository

`MemberRepository` → Spring Data JPA `interface MemberRepository extends JpaRepository<Member, Long>`.

| As-Is method | Target derived query / method |
|---|---|
| `findById(Long)` | `findById(Long)` — inherited from `JpaRepository` |
| `findByEmail(String)` | `Optional<Member> findByEmail(String email)` — returns `Optional` (eliminates `NoResultException` catch pattern) |
| `findAllOrderedByName()` | `List<Member> findAllByOrderByNameAsc()` — derived |

### 2.3 DDL / Schema Strategy

**Decision: Flyway** manages the schema. Owner: Persistence specialist.

- `spring.jpa.hibernate.ddl-auto=validate` in production (Flyway owns DDL; Hibernate validates only).
- `spring.jpa.hibernate.ddl-auto=create-drop` retained for local dev / unit tests only (H2 in-memory).
- `V1__init_aa_registrant.sql` creates the `AA_Registrant` table with all columns and the unique constraint on `email`. The Persistence specialist authors this script.

**Rationale:** `create-drop` is destructive on restart (explicitly flagged as major risk by dependency analyst). Flyway provides audit trail, safe forward-only migrations, and is managed by the Spring Boot 3.4 BOM.

### 2.4 DataSource

JNDI datasource `jdbc/SSA` → Spring Boot `spring.datasource.*` properties in `application.properties`.

```properties
spring.datasource.url=jdbc:...       # placeholder — production DB URL unknown (JNDI target)
spring.datasource.username=...
spring.datasource.password=...
spring.datasource.driver-class-name=...
```

**Note:** The as-is analysis does not identify the production DB engine behind `jdbc/SSA`. The Persistence specialist must obtain this from the operations team and select the appropriate JDBC driver. The driver artifact must be added to `pom.xml` as a runtime dependency outside the Spring Boot BOM.

---

## 3. Transaction Strategy

**Decision: Spring local `@Transactional` — faithful semantic preservation.**

The as-is application uses Container-Managed Transactions (JTA CMT) on a single stateless EJB (`MemberRegistration`) with the default `REQUIRED` attribute. There is **no JMS, no XA, no distributed transaction** anywhere in the codebase. The only transactional resource is the single datasource `jdbc/SSA`.

Single-resource DB transactions under Spring local `@Transactional` are semantically identical to JTA CMT `REQUIRED` in this scenario. There is no downgrade — this is a faithful preservation. No JTA provider (Atomikos, Bitronix) is needed.

- `MemberRegistrationService.register()` is annotated `@Transactional`.
- All other service methods that are read-only are annotated `@Transactional(readOnly = true)`.

---

## 4. Business Logic / Service Layer

**Target:** `@Service MemberRegistrationService` replaces `@Stateless MemberRegistration`.

### 4.1 CDI Event → Spring ApplicationEvent

| As-Is | Target |
|---|---|
| `@Inject Event<Member> memberEventSrc` | `@Autowired ApplicationEventPublisher` |
| `memberEventSrc.fire(member)` | `publisher.publishEvent(new MemberRegisteredEvent(member))` |
| `@Observes(notifyObserver=Reception.IF_EXISTS) Member` | `@EventListener MemberRegisteredEvent` |

`MemberRegisteredEvent` is a plain Java record wrapping `Member`. The `Reception.IF_EXISTS` semantics (only notify if the observer bean exists in context) become irrelevant in Spring Boot — the `@EventListener` method is always present; the UI refresh equivalent is handled by the controller returning the updated list on each request rather than via a cached producer (see §6).

### 4.2 Validation

Bean Validation is applied via `@Valid` on `@RequestBody` in the REST controller and via Spring MVC's validation integration in the Thymeleaf controller. No programmatic `Validator.validate()` call is needed in the service layer.

---

## 5. REST API (Sync Communication)

**Target:** Spring MVC `@RestController` replaces JAX-RS `MemberResourceRESTService` and `MemberResourceRESTServiceSecond`.

The two REST services are functionally identical (only logging verbosity differs). They are **consolidated into a single `MemberRestController`**.

| As-Is | Target |
|---|---|
| `@ApplicationPath("/rest")` + `@Path("/members")` | `@RestController @RequestMapping("/api/members")` |
| `@GET` list | `@GetMapping` → `List<Member>` |
| `@GET /{id}` | `@GetMapping("/{id}")` → `ResponseEntity<Member>` (404 via `ResponseStatusException`) |
| `@POST` create | `@PostMapping` + `@Valid @RequestBody Member` |
| `400` on constraint violations | `@ExceptionHandler(MethodArgumentNotValidException)` in `@RestControllerAdvice` → field→message map |
| `409` on duplicate email | Service throws custom `EmailAlreadyExistsException`; `@ExceptionHandler` → 409 |
| `400` on other exception | `@ExceptionHandler(Exception)` → `{"error": message}` |

**Context root:** The Spring Boot app runs at `/` (no prefix). REST is reachable at `/api/members`. The legacy `/kitchensink-ear-web/rest/members` and `/kitchensink-ear-web2/rest2/members` paths are not preserved — this is an intentional simplification. If compatibility is required, `server.servlet.context-path` or a `@RequestMapping` prefix can be added.

**JSON serialisation:** Jackson (included via `spring-boot-starter-web`). No Gson. No JAXB.

---

## 6. Frontend (UI)

**Target:** Thymeleaf (`spring-boot-starter-thymeleaf`) replaces JSF 2.2 / Facelets.

JSF has no first-class Spring Boot support. Thymeleaf integrates natively with Spring MVC and is managed by the Spring Boot 3.4 BOM.

### 6.1 Controller

`MemberController` (`@Model` CDI) → Spring MVC `@Controller MemberController`.

| As-Is pattern | Target pattern |
|---|---|
| `@Produces @Named Member newMember` | `model.addAttribute("newMember", new Member())` in `@GetMapping` |
| `@Produces @Named List<Member> getMembers()` | `model.addAttribute("members", memberRepository.findAllByOrderByNameAsc())` |
| `MemberListProducer` (CDI observer refresh) | Eliminated — list fetched fresh from DB on each GET (stateless HTTP) |
| `register()` + `FacesMessage` | `@PostMapping` → redirect-after-POST (PRG pattern); Thymeleaf model carries flash message |

`MemberController` and `MemberControllerSecond` are **merged into a single `MemberController`**. The only behavioural difference (DeltaSpike config lookup in `MemberControllerSecond`) is preserved via `@Value` (see §7).

### 6.2 Thymeleaf Templates

| As-Is | Target |
|---|---|
| `WEB-INF/templates/default.xhtml` (Facelets template) | `templates/layout/default.html` (Thymeleaf layout) |
| `index.xhtml` (member form + data table) | `templates/members/index.html` |

---

## 7. Configuration

**Target:** Spring `@Value` and `application.properties` replace Apache DeltaSpike `ConfigResolver`.

```java
// MemberController — replaces DeltaSpike ConfigResolver.getPropertyValue("config.key", "Default value")
@Value("${config.key:Default value}")
private String configKey;
```

`application.properties` declares:
```properties
config.key=Default value
```

Apache DeltaSpike (`deltaspike-core-api`, `deltaspike-core-impl`) is **removed entirely**. No Jakarta-compatible release exists.

---

## 8. Logging

**Target:** Logback via `spring-boot-starter-logging` (SLF4J 2.x API).

| As-Is | Target |
|---|---|
| Log4j 1.2.17 + `slf4j-log4j12` (EOL, CVEs) | Removed — Logback is the Spring Boot default |
| `log4j.xml` in `web/` and `web2/` | Deleted — configure Logback via `logback-spring.xml` or `application.properties` |
| SLF4J 1.7.21 (explicit) | Managed by Spring Boot BOM (SLF4J 2.x) |
| CDI `@Produces Logger produceLog(InjectionPoint)` | Eliminated — each class: `private static final Logger log = LoggerFactory.getLogger(Foo.class)` |
| `prefer-application-packages org.slf4j.*, log4j.*` (WL) | Not needed — single flat JAR, no classloader conflict |

---

## 9. CommonLibsWarForEar Shared Library — Complete Elimination

Every jar bundled in `CommonLibsEar.zip` is accounted for:

| Bundled jar | Disposition |
|---|---|
| `deltaspike-core-api-1.8.2.jar` | **Removed** — replaced by Spring `@Value` |
| `deltaspike-core-impl-1.8.2.jar` | **Removed** |
| `gson-2.8.6.jar` | **Removed** — dead code in `MemberController.initNewMember()` is deleted |
| `log4j-1.2.17.jar` | **Removed** — Logback replaces it |
| `slf4j-log4j12-1.7.21.jar` | **Removed** — not needed with Logback |
| `slf4j-api-1.7.21.jar` | **Replaced** — Spring Boot BOM manages SLF4J 2.x |

`CommonLibsEar.zip` is **deleted from the repository**.  
`weblogic-application.xml` `<library-ref>CommonLibsWarForEar</library-ref>` is **deleted** with the file.

---

## 10. Namespace Migration (javax.* → jakarta.*)

All source files undergo a full namespace rename. Key mappings:

| javax.* | jakarta.* / Spring replacement |
|---|---|
| `javax.persistence.*` | `jakarta.persistence.*` |
| `javax.validation.*` | `jakarta.validation.*` |
| `javax.annotation.*` | `jakarta.annotation.*` |
| `javax.ejb.*` | **Removed** — replaced by Spring `@Service` / `@Transactional` |
| `javax.enterprise.*` (CDI) | **Removed** — replaced by Spring IoC |
| `javax.inject.*` | **Removed** — replaced by `@Autowired` / constructor injection |
| `javax.faces.*` (JSF) | **Removed** — replaced by Thymeleaf / Spring MVC |
| `javax.ws.rs.*` (JAX-RS) | **Removed** — replaced by Spring MVC `@RestController` |
| `javax.xml.bind.*` (JAXB) | **Removed** — `@XmlRootElement` dropped, Jackson only |

Constraint annotation replacements (Hibernate Validator 6.x/8.x removed these):

| Legacy | Replacement |
|---|---|
| `org.hibernate.validator.constraints.Email` | `jakarta.validation.constraints.Email` |
| `org.hibernate.validator.constraints.NotEmpty` | `jakarta.validation.constraints.NotBlank` |

---

## 11. Security

**Decision: No security configuration added.**

The as-is application has zero security: no `<security-constraint>`, no `@RolesAllowed`, no login config. All endpoints are open. The target preserves this posture — Spring Security is **not introduced** by this migration. If security is required, it is a separate initiative.

---

## 12. Tests

**Target:** `@SpringBootTest`, `@DataJpaTest`, `MockMvc` replace Arquillian + ShrinkWrap + JUnit 4.

- JUnit 5 (Jupiter) via `spring-boot-starter-test`.
- H2 in-memory datasource for test profile (`spring.datasource.url=jdbc:h2:mem:testdb`).
- `@DataJpaTest` slices for repository tests.
- `MockMvc` for REST controller tests (no running server needed).
- Testcontainers optional for full integration tests against a real DB.

The Test specialist MUST cover all 26 coverage gaps identified in `reverse_en/test-behavior.md §4` at minimum, prioritising the 16 HIGH items first.

---

## 13. Spring Boot BOM — Managed Dependencies

The following stacks are all managed by the Spring Boot 3.4 BOM — no explicit versions needed:

| Starter / dependency | Replaces |
|---|---|
| `spring-boot-starter-data-jpa` | JPA 2.1 (`provided`) + Hibernate ORM |
| `spring-boot-starter-web` | JAX-RS (`provided`) + Jackson |
| `spring-boot-starter-thymeleaf` | JSF 2.2 (`provided`) |
| `spring-boot-starter-validation` | Hibernate Validator (`provided`) |
| `spring-boot-starter-logging` | SLF4J 1.x + Log4j 1.x |
| `flyway-core` | `hibernate.hbm2ddl.auto` |
| `spring-boot-starter-test` | Arquillian + JUnit 4 |
| `com.h2database:h2` (test scope) | JBoss `test-ds.xml` |

## 14. Compatibility / Out-of-BOM Flags

| Item | Status | Note |
|---|---|---|
| Java 21 + Spring Boot 3.4 | **Fully compatible** — LTS pair, GA combination | No compatibility concern |
| JDBC driver for production DB | **NOT in BOM** — must be added manually | DB engine unknown; obtain from ops team |
| Flyway community edition | In BOM (`flyway-core`) | Free tier sufficient for single-schema migration |
| All other chosen libraries | In BOM | No manual version pinning needed |

---

## 15. Removed / Dead Code

The following items carry no business logic and are deleted, not migrated:

| Item | Reason |
|---|---|
| `MemberController.initNewMember()` Gson block | Confirmed dead code / demo artifact; no business logic |
| `WebResources` (JSF `FacesContext` producer) | JSF eliminated |
| `JaxRsActivator extends Application` | JAX-RS eliminated |
| `Spring Framework 4.3.9.RELEASE` version property | Unused in source; Spring Boot 3.4 brings Spring 6.x |
| `Apache Commons IO`, `Commons Lang3`, `HttpClient` BOM entries | No import in any module source; drop |
| Arquillian / ShrinkWrap test infrastructure | Replaced by Spring Boot test slices |
