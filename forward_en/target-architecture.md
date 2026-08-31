# Target Architecture — kitchensink-ear → Spring Boot 3.4

> **Author:** target-architecture agent · **Loop:** loop-1 · **Date:** 2026-08-31  
> **Basis:** All four reverse-engineering analyses (architecture, business-logic, dependencies, test-behavior)  
> **Target:** Java 21 / Spring Boot 3.4 / Maven 3.9 / Jakarta EE 10  
> **Status:** Awaiting human approval

---

## 1. Module Layout — EAR Collapse Strategy

The multi-module EAR (ear + ejb + web + web2) collapses into a **single Maven module / single Spring Boot fat-jar**.

**Rationale:** The two WARs share 100 % of their business logic through the same EJB module and write to the same table. The cross-WAR session sharing (`sharing-enabled=true`) vanishes automatically when both context paths live in one application. No technical benefit justifies keeping separate deployable modules.

**Target Maven structure:**
```
kitchensink/                    ← single Maven module
├── pom.xml                     ← spring-boot-starter-parent:3.4.x
└── src/main/java/com/example/kitchensink/
    ├── KitchensinkApplication.java
    ├── model/
    │   └── Member.java
    ├── repository/
    │   └── MemberRepository.java
    ├── service/
    │   └── MemberRegistration.java
    ├── event/
    │   └── MemberRegisteredEvent.java
    ├── web/
    │   ├── primary/             ← replaces kitchensink-ear-web
    │   │   ├── MemberController.java
    │   │   └── MemberResourceRESTService.java
    │   └── secondary/           ← replaces kitchensink-ear-web2
    │       ├── MemberControllerSecond.java
    │       └── MemberResourceRESTServiceSecond.java
    └── config/
        └── WebConfig.java       ← context path config
```

**Dependency order for specialists** (each layer compiles against already-migrated code below it):

| Order | Concern | Owner |
|---|---|---|
| 1 | Persistence (entity, schema, datasource) | Persistence Specialist |
| 2 | Service (business logic, events) | Service Specialist |
| 3 | Web / REST API | Web / API Specialist |
| 4 | UI (Thymeleaf views + controllers) | UI Specialist |
| 5 | Tests | Test Specialist |

---

## 2. Persistence

| Decision | Choice | Justification |
|---|---|---|
| ORM | Spring Data JPA (`spring-boot-starter-data-jpa`) | Replaces JPA 2.1 + Hibernate via JBoss BOM; Hibernate 6.x managed by Spring Boot BOM |
| API namespace | `jakarta.persistence.*` | Full `javax.*` → `jakarta.*` rename required across all 14 production files |
| Datasource config | `spring.datasource.*` in `application.properties` | Replaces JNDI `jdbc/SSA` defined in WebLogic; no JNDI needed |
| DB dialect | Auto-detected by Hibernate 6.x | No explicit dialect set in current `persistence.xml`; Hibernate auto-detection is correct |
| `@XmlRootElement` on `Member` | Remove; use Jackson `@JsonProperty` / default serialisation | JAXB annotation was only needed for JAX-RS XML output; Spring MVC + Jackson handles JSON natively |

### DDL / Schema Strategy

**Owner: Persistence Specialist.**

Replace `hibernate.hbm2ddl.auto=create-drop` (dev/demo artefact) with **Flyway** (`flyway-core` on classpath).

- Initial migration: `V1__create_aa_registrant.sql` — creates table `AA_Registrant` with `id`, `name`, `email` (unique), `phone_number`.
- Set `spring.jpa.hibernate.ddl-auto=validate` so Hibernate validates against the Flyway-managed schema.
- Set `spring.flyway.locations=classpath:db/migration`.

**No Liquibase.** Flyway is the simpler default for a single-entity schema with no branch merging concerns.

---

## 3. Transaction Strategy

**Decision: Spring local `@Transactional` — faithful replacement, no semantic downgrade.**

The current application uses JTA/CMT exclusively through `@Stateless` EJB (`MemberRegistration.register()` with default `REQUIRED` attribute). However:

- There is **no JMS** in this application.
- There is **no second XA-capable resource** (only one datasource: `jdbc/SSA`).
- XA / two-phase commit was therefore never actually exercised — the JTA runtime was present only as container overhead.

Replacing with Spring's local `@Transactional` (JDBC/Hibernate-managed, not JTA) preserves all observable behaviour:
- `REQUIRED` semantics → default Spring `@Transactional(propagation = REQUIRED)`.
- Rollback-on-exception → Spring rolls back on unchecked exceptions by default (same as CMT).
- `MemberRepository` participates in the ambient transaction because it is injected into the same `@Transactional` service call.

**No XA datasource, no JTA provider (Atomikos / Bitronix) needed.**

---

## 4. Business Logic / Service Layer

| Current | Target | Notes |
|---|---|---|
| `MemberRegistration` `@Stateless` EJB | `@Service` + `@Transactional` | Identical contract; remove `@Stateless`, add Spring annotations |
| `MemberRepository` CDI `@ApplicationScoped` | `@Repository` (Spring-managed) or `JpaRepository<Member,Long>` | Criteria API queries can be kept or replaced with Spring Data derived queries |
| `MemberListProducer` CDI `@RequestScoped` + `@Produces @Named` | Eliminate the producer bean | JSF EL expressions disappear with Thymeleaf; the controller can call the service directly |
| CDI `Event<Member>` / `@Observes` | `ApplicationEventPublisher.publishEvent(new MemberRegisteredEvent(member))` + `@EventListener` | Direct replacement; `IF_EXISTS` semantics not needed (Spring events are synchronous by default) |
| `Resources` CDI producer (EntityManager, Logger) | Remove | Spring injects `EntityManager` via `@PersistenceContext`; SLF4J logger via `LoggerFactory`/Lombok `@Slf4j` |

---

## 5. Web / REST API Layer

**Decision: Spring MVC `@RestController`** — not Jersey.

Rationale: Jersey adds an extra layer (JAX-RS runtime) on top of Spring MVC's DispatcherServlet. For a simple CRUD REST surface, Spring MVC controllers are idiomatic Spring Boot and require fewer moving parts.

| Current | Target |
|---|---|
| `JaxRsActivator` `@ApplicationPath("/rest")` | Remove; use `@RequestMapping("/rest")` on each controller |
| `MemberResourceRESTService` (JAX-RS) | `@RestController @RequestMapping("/web/rest")` |
| `MemberResourceRESTServiceSecond` (JAX-RS) | `@RestController @RequestMapping("/web2/rest")` |
| `@GET`, `@POST`, `@Path` | `@GetMapping`, `@PostMapping`, `@PathVariable` |
| `Response` builder | `ResponseEntity<>` |
| `Validator` (manual injection) | `@Valid` on `@RequestBody` + `BindingResult` |
| `@XmlRootElement` JSON output | Jackson default serialisation |

**Context paths:** The original context roots (`/kitchensink-ear-web`, `/kitchensink-ear-web2`) can be shortened to `/web` and `/web2` via `server.servlet.context-path` considerations — or kept as-is via path prefixes. Exact paths are a deployment decision; the specialist should expose them as a configurable `application.properties` property.

**Response contract is preserved:**
- `GET /members` → 200 JSON array
- `GET /members/{id:[0-9]+}` → 200 JSON or 404
- `POST /members` → 200 success / 400 field errors / 409 duplicate email

---

## 6. UI Layer

**Decision: Thymeleaf** via `spring-boot-starter-thymeleaf`.

JSF (Facelets) has no Spring Boot starter and requires a separate Jakarta Faces runtime. Thymeleaf is the idiomatic Spring Boot view technology.

| Current JSF | Target Thymeleaf |
|---|---|
| `@Model` (= `@Named @RequestScoped`) | `@Controller @RequestScope` |
| `FacesContext` producer | Remove; use `Model` / `RedirectAttributes` |
| `faces-config.xml`, `weblogic.xml` JSP settings | Delete entirely |
| Error messages via `FacesContext.addMessage` | Spring MVC `BindingResult` + Thymeleaf `th:errors` |
| Root-cause chain display | `@ExceptionHandler` in each controller or `@ControllerAdvice` |

**`MemberController.initNewMember()` Gson debug log** — keep as-is with Gson 2.10.x dependency declared explicitly (see §9).

**`MemberControllerSecond.initNewMember()` DeltaSpike config** — replace with `@Value("${config.key:Default value}")` injected field. Property `config.key` must appear in `application.properties` (or remain absent to use the default).

---

## 7. Configuration

| Current | Target |
|---|---|
| DeltaSpike `ConfigResolver.getPropertyValue("config.key", "Default value")` | `@Value("${config.key:Default value}")` |
| No `*.properties` in repo | `src/main/resources/application.properties` with `config.key=<value>` (optional; default handles absence) |
| WebLogic `weblogic-application.xml` | Delete |
| WebLogic `weblogic.xml` (both WARs) | Delete |
| `auto-deploy` Maven profile | Delete |

---

## 8. Logging

| Current | Target |
|---|---|
| SLF4J 1.7.21 + Log4j 1.2.17 + `slf4j-log4j12` bridge | Spring Boot default: SLF4J 2.x + **Logback** (managed by Spring Boot BOM) |
| `WEB-INF/classes/log4j.xml` in each WAR | Delete; use `src/main/resources/logback-spring.xml` |
| `Resources` CDI `Logger` producer | Remove; use `LoggerFactory.getLogger(getClass())` or Lombok `@Slf4j` |
| WebLogic `prefer-application-packages` for `org.slf4j.*`, `log4j.*` | Delete (conflict disappears without the server) |

---

## 9. Security

**No Spring Security introduced.** The current application has no security constraints (`web.xml`, EJB annotations, or `@RolesAllowed`). Security was entirely server-managed at the WebLogic layer outside the application. Introducing Spring Security is a new feature, not a migration concern — out of scope.

---

## 10. Session Management

Cross-WAR session sharing (`sharing-enabled=true`) **disappears by design** when the two WARs are merged into a single Spring Boot application. A single standard HTTP session (in-memory, embedded Tomcat default) replaces the WebLogic in-memory session store. No Spring Session needed.

---

## 11. CommonLibsEar — Explicit Replacements

The `CommonLibsEar.zip` shared-library mechanism does not exist in Spring Boot. All jars it provided must become explicit Maven dependencies or be removed.

| Jar in CommonLibsEar | Replacement |
|---|---|
| `deltaspike-core-api-1.8.2.jar` | **Remove** — config usage replaced by `@Value` (§7) |
| `deltaspike-core-impl-1.8.2.jar` | **Remove** |
| `gson-2.8.6.jar` | `com.google.code.gson:gson:2.10.1` — explicit dependency, NOT in Spring Boot BOM |
| `log4j-1.2.17.jar` | **Remove** — replaced by Logback (Spring Boot BOM) |
| `slf4j-api-1.7.21.jar` | Managed by Spring Boot BOM (2.x) — no explicit declaration needed |
| `slf4j-log4j12-1.7.21.jar` | **Remove** |
| `CommonLibsWarForEar.war` | Delete `CommonLibsEar.zip` entirely |

---

## 12. Full Dependency Manifest

### Spring Boot starters (all managed by `spring-boot-starter-parent:3.4.x`)

| Starter | Replaces |
|---|---|
| `spring-boot-starter-web` | JAX-RS runtime, servlet container |
| `spring-boot-starter-data-jpa` | `hibernate-jpa-2.1-api`, `hibernate-entitymanager`, `cdi-api` |
| `spring-boot-starter-validation` | `hibernate-validator` 5.x, `javax.validation` |
| `spring-boot-starter-thymeleaf` | JSF runtime, Facelets |
| `spring-boot-starter-test` | JUnit 4, Arquillian |
| `flyway-core` | `hbm2ddl.auto=create-drop` |

### Explicit (NOT in Spring Boot BOM — version must be declared)

| Artifact | Version | Replaces |
|---|---|---|
| `com.google.code.gson:gson` | 2.10.1 | `gson-2.8.6.jar` from CommonLibsEar |
| `commons-io:commons-io` | 2.16.1 | `commons-io:2.5` — CVE fixes |

### Upgrade / replace (verify BOM coverage)

| Artifact | Action |
|---|---|
| `org.apache.commons:commons-lang3` | Bump to 3.14+ — check if Spring Boot BOM manages it; if not, pin explicitly |
| `org.apache.httpcomponents.client5:httpclient5` | Replace `httpclient:4.5.3` (EOL) with HttpClient 5.x or Spring `RestClient` / `WebClient` |
| `commons-logging:commons-logging` | Remove; Spring Boot BOM excludes it in favour of `jcl-over-slf4j` |

### Remove entirely

- `org.wildfly.bom:wildfly-javaee7-with-tools`
- `org.jboss.spec:jboss-javaee-7.0`
- `org.jboss.spec.javax.ejb:jboss-ejb-api_3.2_spec`
- `org.jboss.spec.javax.faces:jboss-jsf-api_2.2_spec`
- `org.jboss.spec.javax.ws.rs:jboss-jaxrs-api_2.0_spec`
- `org.hibernate.javax.persistence:hibernate-jpa-2.1-api`
- `com.oracle.weblogic:weblogic-maven-plugin`
- `org.jboss.arquillian.*` (all artefacts)
- `junit:junit:4.12`
- `org.apache.deltaspike.core:deltaspike-core-api`
- `org.apache.deltaspike.core:deltaspike-core-impl`
- `log4j:log4j`, `org.slf4j:slf4j-log4j12`

### Leftover property to investigate

`version.spring.framework=4.3.9.RELEASE` exists in root `pom.xml` but is not wired to any dependency — verify unused and delete.

---

## 13. Compatibility / Out-of-BOM Section

| Concern | Status | Action required |
|---|---|---|
| `com.google.code.gson:gson:2.10.1` | **NOT in Spring Boot BOM** | Declare explicit version in POM |
| `commons-io:commons-io:2.16.1` | **NOT in Spring Boot BOM** | Declare explicit version in POM |
| `org.apache.commons:commons-lang3` | Managed by Spring Boot BOM from 3.3.x via `commons-lang3.version` property | Verify actual BOM-managed version ≥ 3.14; override property if needed |
| `httpclient5` | NOT in Spring Boot BOM 3.4 | Declare explicit version or switch to Spring `RestClient` (zero extra dep) |
| Hibernate Validator 8.x (`@NotEmpty` legacy) | In BOM | Replace `@NotEmpty` with `@NotBlank` on `email` field (test-behavior analysis §4) |
| Java 21 — removed `javax.*` APIs | Entire `javax.*` namespace absent from JDK 21 classpath | Full package rename required; confirmed by test-behavior analysis (build probe failure) |
| Spring Boot 3.4 / Jakarta EE 10 | All Jakarta APIs in BOM | No version declarations needed for Jakarta APIs |

---

## 14. Test Baseline

The Arquillian `MemberRegistrationIT` must be **rewritten** (not migrated) as Spring Boot tests.

Coverage gaps identified in `reverse_en/test-behavior.md` §3 must become the **minimum acceptance test suite** for the migration.

| Test slice | Scope |
|---|---|
| `@DataJpaTest` | `MemberRepository` — all 3 queries + edge cases (null, not-found, NonUniqueResult) |
| `@WebMvcTest` | `MemberResourceRESTService` — all 3 endpoints, all error paths (400, 409, 404) |
| `@SpringBootTest` | `MemberRegistration` — register flow, event publication, transaction rollback on constraint violation |

Owner: **Test Specialist** (after Persistence + Service layers are done).
