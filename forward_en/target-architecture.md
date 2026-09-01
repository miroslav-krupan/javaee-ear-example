# Target Architecture — kitchensink-ear → kitchensink-springboot

> Produced by the Target Architecture agent from all four reverse-engineering analyses.
> **Human approval required before implementation begins.**
> Issue: #9 | Loop: loop-1 | Date: 2026-09-01

---

## 1. Overview

The four-module EAR (ejb + web + web2 + ear assembly) collapses into a **single Spring Boot 3.4 executable JAR** (`kitchensink-springboot`). The duplicate web/web2 WAR pair merges into one application — the only behavioural difference between them (DeltaSpike `ConfigResolver` in web2) is preserved via Spring's `@Value`. Java source and target: **21**.

---

## 2. Build & Packaging

| Concern | Legacy | Target |
|---|---|---|
| Parent POM | WildFly BOM (`wildfly-javaee7-with-tools`) | `spring-boot-starter-parent 3.4.x` |
| Packaging | Multi-module EAR (ejb + 2×WAR + ear) | **Single-module Spring Boot fat JAR** |
| Maven version | 3.x (legacy) | **Maven 3.9** |
| Java source/target | 8 | **21** |
| Artifact ID | — | `kitchensink-springboot` (`com.example` group) |

**Dependencies to remove entirely:**

- `com.oracle.weblogic:weblogic-maven-plugin` — entire `auto-deploy` Maven profile
- `version.spring.framework=4.3.9.RELEASE` property — declared but not used; Spring version managed by Boot parent
- `org.apache.httpcomponents:httpclient`, `commons-io`, `commons-lang3` — declared, unused
- `log4j:log4j:1.2.17` + `org.slf4j:slf4j-log4j12` — EOL / CVE; replaced by Logback
- All JBoss/WildFly spec JARs (EJB, JSF, JAX-RS API specs; `wildfly-javaee7-with-tools` BOM; `jboss-javaee-7.0` BOM)
- `org.apache.deltaspike:deltaspike-core-api/impl` — CDI extension; incompatible with Spring Boot
- `org.jboss.arquillian.*` + ShrinkWrap — replaced by Spring Test
- `com.google.code.gson:gson` — dead debug code only; removed (see §5)

---

## 3. Target Module Layout

### 3.1 Source tree

Single Maven module at the repo root (no sub-modules):

```
kitchensink-springboot/
├── pom.xml
└── src/
    ├── main/
    │   ├── java/com/example/kitchensink/
    │   │   ├── KitchensinkApplication.java        (@SpringBootApplication)
    │   │   ├── domain/
    │   │   │   └── Member.java                    (@Entity, jakarta.* constraints)
    │   │   ├── repository/
    │   │   │   └── MemberRepository.java          (JpaRepository<Member,Long>)
    │   │   ├── service/
    │   │   │   ├── MemberRegistration.java        (@Service @Transactional)
    │   │   │   └── MemberRegisteredEvent.java     (ApplicationEvent)
    │   │   └── web/
    │   │       ├── rest/
    │   │       │   └── MemberRestController.java  (@RestController /rest/members)
    │   │       └── ui/
    │   │           └── MemberController.java      (@Controller, Thymeleaf)
    │   └── resources/
    │       ├── application.properties
    │       ├── db/migration/                      (Flyway SQL scripts — owned by Persistence)
    │       └── templates/                         (Thymeleaf .html views)
    └── test/
        └── java/com/example/kitchensink/         (JUnit 5 + Spring Test slices)
```

### 3.2 Dependency order of concerns (specialist sequencing)

Each specialist tier compiles against already-migrated code from the tier(s) before it.

| Order | Concern | Owner specialist | Depends on |
|---|---|---|---|
| 0 | **Domain** — `Member` entity, Jakarta constraint migration, `@XmlRootElement` removal | Persistence | — |
| 1 | **Persistence** — `MemberRepository` (JpaRepository), Flyway schema, datasource config | Persistence | Domain (0) |
| 2 | **Business logic** — `MemberRegistration`, `MemberRegisteredEvent`, event wiring | Business Logic | Persistence (1) |
| 3 | **Web / API / UI** — `MemberRestController`, `MemberController` (Thymeleaf), Spring MVC config | Sync Comm + Frontend | Business Logic (2) |

---

## 4. Persistence

| Concern | Decision | Rationale |
|---|---|---|
| ORM / JPA | **Spring Data JPA** (`spring-boot-starter-data-jpa`) | Replaces JPA 2.1 / Hibernate provided by container |
| Repository style | **`JpaRepository<Member, Long>`** with derived queries | `findByEmail`, `findAllByOrderByNameAsc`, `findById` map directly to Spring Data derived-query naming; replaces Criteria API |
| Jakarta namespace | All `javax.persistence.*` → `jakarta.persistence.*` | Spring Boot 3.x / Jakarta EE 10 requirement; applies across all three legacy modules |
| Schema management | **Flyway** (`spring-boot-starter`) | Explicit SQL migration scripts under `db/migration/`. Replaces `hbm2ddl.auto=create-drop`. **Persistence specialist is the sole DDL owner.** |
| DDL strategy (prod) | `spring.jpa.hibernate.ddl-auto=validate` | Flyway owns schema; Hibernate validates only |
| DDL strategy (test) | Flyway applied to H2 in-memory DB | Consistent schema path for both prod and test |
| Production datasource | `spring.datasource.*` in `application.properties` | Replaces JNDI `jdbc/SSA`; actual JDBC driver + URL TBD by platform team (H2 kept as dev default) |
| Test datasource | `jdbc:h2:mem:test` | Replaces `java:jboss/datasources/KitchensinkEarQuickstartTestDS`; configured in `application-test.properties` |
| Bean Validation — `@Email` | `jakarta.validation.constraints.Email` | `org.hibernate.validator.constraints.Email` removed in HV 8.x — compile error if retained (**blocker**) |
| Bean Validation — `@NotEmpty` | `jakarta.validation.constraints.NotBlank` | `org.hibernate.validator.constraints.NotEmpty` similarly removed (**blocker**) |
| `@XmlRootElement` | **Remove** | Jackson handles JSON serialisation; JAXB annotation has no function in Spring Boot |
| Table / column names | `AA_Registrant`, same column names | Preserved exactly in the Flyway DDL migration script |

---

## 5. Business Logic

| Concern | Legacy | Target |
|---|---|---|
| Service bean | `@Stateless` EJB | `@Service` (Spring stereotype) |
| Transaction | CMT `REQUIRED` (default on all EJB methods) | `@Transactional` at class level on `MemberRegistration` — same semantics |
| Post-register event | `javax.enterprise.event.Event<Member>.fire()` | `ApplicationEventPublisher.publishEvent(new MemberRegisteredEvent(member))` |
| Event observer | `@Observes(Reception.IF_EXISTS) Member` on `MemberListProducer` | `@EventListener` on the equivalent Spring component |
| `EntityManager` injection | CDI producer in `Resources` | Auto-wired via `@PersistenceContext` or Spring Data repository abstraction (no explicit EM injection needed in service layer) |
| SLF4J logger injection | CDI producer in `Resources` | `private static final Logger log = LoggerFactory.getLogger(X.class)` — CDI producer pattern removed |
| DeltaSpike config (`web2`) | `ConfigResolver.getPropertyValue("config.key", "Default value")` | `@Value("${config.key:Default value}")` injected into `MemberController`; property entry added to `application.properties` |
| `apache-deltaspike.properties` content | `web2/src/main/resources/META-INF/apache-deltaspike.properties` | Merge key-value pairs into `application.properties` |
| Gson debug code (`web`) | `new Gson().toJson(testMember)` in `@PostConstruct initNewMember()` | **Remove entirely** — confirmed dead debug code, no production value; Gson dependency dropped |

---

## 6. Web / API / Frontend

### 6.1 REST API

| Concern | Legacy (JAX-RS) | Target (Spring MVC) |
|---|---|---|
| Framework | JAX-RS via `JaxRsActivator extends Application` | Spring MVC (`spring-boot-starter-web`), auto-configured |
| Base path | `/kitchensink-ear-web/rest/members` | `/rest/members` (context path `/`) |
| Controller | `@RequestScoped @Path("/members")` | `@RestController @RequestMapping("/rest/members")` |
| `GET /members` | `Response.ok(list)` | `ResponseEntity<List<Member>>` |
| `GET /members/{id}` | `Response.ok(m)` / 404 | `ResponseEntity<Member>` |
| `POST /members` | Manual `Validator.validate()` + explicit `emailAlreadyExists` pre-check | **Same explicit flow preserved** — keeps exact 400 (violation map) / 409 (`{"email":"Email taken"}`) / 400 (`{"error":"<msg>"}`) response shapes |
| JSON serialisation | JAXB / JAX-RS container marshaller | **Jackson** (Boot default) |
| web vs web2 REST | Two identical services in two WARs | **Single `MemberRestController`** — the two REST services were functionally identical |

### 6.2 Frontend (JSF → Thymeleaf)

| Concern | Legacy (JSF Facelets) | Target (Thymeleaf) |
|---|---|---|
| Template engine | JSF 2.2 Facelets (`.xhtml`) | **Thymeleaf 3.x** (`spring-boot-starter-thymeleaf`), standard HTML5 |
| Registration form | `h:inputText` bound to `#{newMember.name}` etc. | `<input th:field="*{name}">` bound to model attribute |
| Member list | `h:dataTable` over `#{members}` | `<tr th:each="m : ${members}">` |
| Controller stereotype | `@Model` (`@RequestScoped + @Named`) | `@Controller` with `Model` parameter |
| Form feedback | `h:messages` / `javax.faces.application.FacesMessage` | `BindingResult` errors surfaced in model; success via `RedirectAttributes` flash message |
| Master layout | `ui:composition` Facelets template | Thymeleaf fragment/layout (`th:replace` or layout dialect) |
| Post-registration refresh | CDI event → `MemberListProducer` observer (same request) | Spring `ApplicationEvent` → `@EventListener` refreshes list; or redirect-after-POST pattern |

**Thymeleaf chosen over SPA (Vue/React):** server-side rendering preserves the existing "form + list on one page" UX with no frontend build toolchain. A SPA would require a separate build pipeline disproportionate to the app's scale and the migration's scope.

### 6.3 Context path

Single Spring Boot app, context path **`/`** by default. The legacy dual context roots `/kitchensink-ear-web` and `/kitchensink-ear-web2` collapse into one. Configure via `server.servlet.context-path` in `application.properties` if a path prefix is required by deployment.

---

## 7. Transaction Strategy

**Decision: Spring local `@Transactional` (no JTA / XA).**

The legacy app uses CMT JTA solely because EJB containers require it — the actual boundary is always a single datasource (`jdbc/SSA`). There is no JMS, no second XA resource, no distributed transaction requirement in the codebase. Spring's `JpaTransactionManager` (local) is semantically equivalent for single-resource workloads and requires no XA infrastructure. **This is a faithful semantic downgrade**: no behavior changes under normal operation, significantly reduced operational complexity.

---

## 8. Logging

| Item | Decision |
|---|---|
| API | SLF4J 2.x (managed by Spring Boot BOM) |
| Implementation | **Logback** via `spring-boot-starter-logging` (Boot default) |
| Configuration | `application.properties` logging properties; `logback-spring.xml` if custom appenders needed |
| Log4j 1.x (`log4j:log4j:1.2.17`) | **Removed** — EOL 2015, multiple CVEs |
| `slf4j-log4j12` bridge | **Removed** |
| `prefer-application-packages` for `org.slf4j.*`, `log4j.*` | **Moot** — WebLogic descriptor deleted; Boot classpath is self-contained |
| CDI Logger producer (`Resources.produceLog`) | **Removed** — replaced by `private static final Logger log = LoggerFactory.getLogger(X.class)` per class |

---

## 9. Configuration

| Legacy item | Spring Boot equivalent |
|---|---|
| JNDI `jdbc/SSA` datasource | `spring.datasource.url/username/password/driver-class-name` in `application.properties` |
| `web.xml` session timeout (30 min) | `server.servlet.session.timeout=30m` |
| WebLogic EAR cross-module session sharing | **Not applicable** — single application |
| WebLogic `prefer-application-packages` | **Not applicable** — no app server |
| DeltaSpike `config.key` property | `config.key=Default value` in `application.properties` |
| Log4j configuration (unlocated; possibly in `CommonLibsEar.zip`) | `application.properties` logging levels + Logback |
| `faces-config.xml` | **Deleted** — JSF removed |
| All `weblogic*.xml` descriptors | **Deleted** |
| `application.xml` (EAR) | **Deleted** |

---

## 10. Security

No security was configured in the legacy app — no `@RolesAllowed`, no `web.xml` security constraints, no WebLogic security policies. The Spring Boot target **does not add Spring Security** for this migration. Behavior is preserved exactly. A separate story must add authentication/authorization if required.

---

## 11. CommonLibsWarForEar — Open Risk (Blocker)

The WebLogic shared library `CommonLibsWarForEar 1.0` is referenced in `weblogic-application.xml` but its archive (`original_app/CommonLibsEar.zip`) is not a valid ZIP file and cannot be statically inspected.

**Action required before the build can be marked complete:**

- Platform team must identify the contents of `CommonLibsWarForEar` from the original WebLogic server installation.
- Any library it contributes that is actively used by the migrated application must be added as an explicit `<dependency>` in the Spring Boot `pom.xml`.
- Until resolved, the Dependency Specialist raises a `blocker` finding in `validation/findings/`.

---

## 12. Compatibility / Out-of-BOM

| Library | In Spring Boot 3.4 BOM? | Note |
|---|---|---|
| Hibernate Validator 8.x | Yes (`spring-boot-starter-validation`) | Replaces `org.hibernate.validator.constraints.*` legacy annotations |
| Flyway | Yes | Version managed by Boot BOM |
| Thymeleaf 3.x | Yes (`spring-boot-starter-thymeleaf`) | — |
| H2 | Yes (test default) | — |
| SLF4J 2.x | Yes | — |
| Logback | Yes | — |
| Jackson 2.x | Yes (`spring-boot-starter-web`) | Replaces JAXB/Gson |
| Gson | **Not in BOM; not needed** | Removed — dead code only |
| DeltaSpike | **Not compatible** | CDI extension; removed entirely |
| Log4j 1.x | **Not in BOM; do not add** | EOL; removed |
| Arquillian | **Not in BOM** | Replaced by Spring Test |
| `CommonLibsWarForEar` contents | **Unknown** | See §11 |

---

## 13. Test Strategy

The single Arquillian container IT is replaced by a layered JUnit 5 suite:

| Layer | Tool | Scope |
|---|---|---|
| Unit | JUnit 5 + Mockito | Service and controller logic in isolation |
| Repository slice | `@DataJpaTest` + H2 | JPA queries, constraint validation, uniqueness enforcement |
| Web / API slice | `@WebMvcTest` + MockMvc | REST endpoint shapes: 200, 400 (violation map), 409, 404 |
| Full integration | `@SpringBootTest` + H2 | End-to-end registration flow including Flyway migration |

The **26 coverage gaps** identified in `reverse_en/test-behavior.md` §3 are the mandatory minimum test list for the Test Specialist.
