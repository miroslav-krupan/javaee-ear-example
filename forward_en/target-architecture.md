# Target Architecture — kitchensink-ear → Spring Boot 3.4

> **Status:** awaiting human approval  
> Generated: 2026-09-01 | Loop: loop-1 | Issue: #11  
> Source analyses: `reverse_en/architecture.md`, `reverse_en/business-logic.md`, `reverse_en/test-behavior.md`  
> Dependency source: derived directly from `original_app/` POMs (dependency analyst produced no output file)

---

## 1. Build & Packaging

| Attribute | Legacy | Target |
|---|---|---|
| Build tool | Maven 3, multi-module POM | Maven 3.9, **single-module** POM |
| Java version | 8 | **Java 21** |
| Runtime | WildFly 11 / WebLogic (EAR) | **Spring Boot 3.4 fat JAR** (embedded Tomcat) |
| Java EE spec | Java EE 7 (`javax.*`) | **Jakarta EE 10** (`jakarta.*`) |
| Artifact | `kitchensink-ear.ear` (4 modules) | `kitchensink.jar` (1 module) |

**Collapse decision:** The 4-module EAR (`ejb` + `web` + `web2` + `ear`) collapses into a single Maven module. The EJB module becomes a `service/` package; both WARs merge into one Spring Boot web layer. No EAR assembler is needed.

---

## 2. Dependency Map — Every Legacy Dep Accounted For

### 2a. Container-provided Java EE APIs → Spring Boot starters

| Legacy artifact | Status | Target |
|---|---|---|
| `jboss-javaee-7.0` BOM (all `provided` APIs) | **Replace** | `spring-boot-starter-parent` 3.4 BOM |
| `javax.enterprise:cdi-api` | **Replace** | Spring DI (`@Autowired`, `@Component`, `@Service`) |
| `javax.ejb` (`@Stateless`, CMT) | **Replace** | `@Service` + `@Transactional` (Spring) |
| `javax.faces` (JSF 2.2) | **Replace** | `spring-boot-starter-thymeleaf` |
| `javax.ws.rs` (JAX-RS 2.0 / RESTEasy) | **Replace** | `spring-boot-starter-web` (Spring MVC `@RestController`) |
| `javax.persistence` (JPA 2.1 via Hibernate) | **Replace** | `spring-boot-starter-data-jpa` (Jakarta Persistence 3.1) |
| `org.hibernate:hibernate-validator` (BV provided) | **Replace** | `spring-boot-starter-validation` (Hibernate Validator 8 via BOM) |
| `org.slf4j:slf4j-api` 1.7.21 (provided) | **Replace** | SLF4J 2.x (via `spring-boot-starter-logging`, in BOM) |
| `org.slf4j:slf4j-log4j12` 1.7.21 (EAR, provided) | **Remove** | Logback is the Spring Boot default; no log4j bridge needed |
| `log4j:log4j` 1.2.17 (EAR, provided) | **Remove** | Logback (in BOM, auto-configured) |
| `junit:junit` 4.x (test) | **Replace** | JUnit 5 (`junit-jupiter`, in BOM) |
| Arquillian + ShrinkWrap (test) | **Remove** | Spring Boot Test slices (see §8) |

### 2b. Application-owned shared-library deps

Both `web` and `web2` declared Gson and DeltaSpike at `provided` scope, meaning they came from the WebLogic shared library `CommonLibsWarForEar` (v1.0). The EAR also declared them `provided`.

| Library | Version | Usage | Decision |
|---|---|---|---|
| `com.google.code.gson:gson` | 2.8.6 | `MemberController.@PostConstruct` — serialises a test `Member` to JSON and logs it. Dead-code diagnostic only; no functional impact. | **Remove entirely.** Jackson (Spring Boot default, in BOM) is available if JSON serialisation is ever needed in a controller. The diagnostic log block is deleted. |
| `org.apache.deltaspike.core:deltaspike-core-api` | 1.8.2 | `ConfigResolver.getPropertyValue("config.key", "Default value")` in `MemberControllerSecond.@PostConstruct` | **Replace** with Spring `@Value("${config.key:Default value}")`. Property migrated to `application.properties`. |
| `org.apache.deltaspike.core:deltaspike-core-impl` | 1.8.2 | DeltaSpike runtime | **Remove** (DeltaSpike not compatible with Jakarta EE 10; not needed after `@Value` migration) |

**`CommonLibsWarForEar` shared library (WebLogic `<library-ref>`):** has no Spring Boot equivalent and is not needed. Its two contents are either removed (Gson) or replaced (DeltaSpike). No shared-library mechanism is required; all deps are bundled in the fat JAR via Maven.

### 2c. H2 Database

Legacy used H2 1.4.193 (container-managed, JNDI `jdbc/SSA`). Spring Boot BOM manages H2 2.x. H2 stays as the embedded database for dev/test. A Spring profile (`prod`) may override with a real DB datasource via `application-prod.properties`.

### 2d. Out-of-BOM additions

**None required.** Every dependency is either removed, replaced by a Spring Boot BOM-managed artifact, or already in the BOM. The project requires zero out-of-BOM version pins post-migration.

---

## 3. Transaction Strategy

**Decision: downgrade from JTA/XA to Spring local `@Transactional`.**

**Rationale:** The application has exactly one data source (`jdbc/SSA` → `spring.datasource.*`) and no JMS or XA participants. Container-managed transactions (CMT via `@Stateless`) are a faithful equivalent of Spring's local `@Transactional(propagation=REQUIRED)` (the default). There is no semantic change — the transaction boundary is the same service method boundary.

- `@Stateless` + CMT `REQUIRED` on `MemberRegistration.register()` → `@Service` + `@Transactional` on the same method.
- `@ApplicationScoped` `MemberRepository` reads are non-transactional by nature; no `@Transactional` needed on read-only queries (Hibernate opens its own session).
- No JTA, no Atomikos, no XA datasource, no `JtaTransactionManager`. Spring Boot auto-configures `DataSourceTransactionManager`.

---

## 4. DDL / Schema Strategy & DB Dialect

**Owner:** Persistence Specialist.

| Attribute | Legacy | Target |
|---|---|---|
| Schema management | `hibernate.hbm2ddl.auto=create-drop` | **Flyway** (single `V1__init.sql`) |
| Dev/test DB | H2 in-memory (JNDI `jdbc/SSA`) | H2 in-memory (`spring.datasource.url=jdbc:h2:mem:kitchensink`) |
| Dialect | Hibernate auto-detected from WildFly | Spring Boot auto-detects from datasource driver; explicit `spring.jpa.database-platform` not required for H2 |
| Table | `AA_Registrant` (keep exact name — unique constraint on `email`) | `AA_Registrant` — preserved verbatim in Flyway migration |
| `show_sql` | `true` | `spring.jpa.show-sql=true` (dev only; set `false` in prod profile) |

`ddl-auto` must NOT be used in the target. Flyway manages schema evolution explicitly. `spring.jpa.hibernate.ddl-auto=validate` is acceptable alongside Flyway for safety.

---

## 5. Module & Package Layout

The single Maven module is `com.example.kitchensink`. Package structure:

```
com.example.kitchensink
├── KitchensinkApplication.java       (@SpringBootApplication)
├── model/
│   └── Member.java                   (@Entity, @Table("AA_Registrant"), Bean Validation)
├── repository/
│   └── MemberRepository.java         (Spring Data JPA or plain @Repository)
├── service/
│   ├── MemberRegistration.java       (@Service, @Transactional)
│   └── MemberRegisteredEvent.java    (Spring ApplicationEvent)
├── web/
│   ├── rest/
│   │   ├── MemberRestController.java  (primary REST — maps to /rest/members)
│   │   └── MemberRestControllerV2.java (secondary REST — maps to /rest2/members)
│   └── ui/
│       └── MemberController.java     (Thymeleaf controller — maps to /kitchensink-ear-web)
└── config/
    └── AppConfig.java                (@Configuration beans if needed)
```

Resources:
```
src/main/resources/
├── application.properties
├── application-prod.properties       (prod datasource override)
├── db/migration/V1__init.sql         (Flyway: CREATE TABLE AA_Registrant)
└── templates/                        (Thymeleaf templates, replacing *.xhtml)
```

---

## 6. Dependency Order for Specialist Sequencing

Specialists must deliver in this order so each compiles against already-migrated code:

| Order | Specialist | What they deliver |
|---|---|---|
| 1 | **Persistence Specialist** | `model/Member.java` (Jakarta Persistence 3.1, jakarta.validation), `repository/MemberRepository.java`, Flyway `V1__init.sql`, `application.properties` datasource config |
| 2 | **Business Logic Specialist** | `service/MemberRegistration.java`, `service/MemberRegisteredEvent.java`; depends on model + repository |
| 3 | **Sync-Comm / REST Specialist** | `web/rest/MemberRestController.java`, `web/rest/MemberRestControllerV2.java`; depends on service + repository |
| 4 | **Frontend Specialist** | `web/ui/MemberController.java`, Thymeleaf templates; depends on service |
| 5 | **Security Specialist** | `spring-boot-starter-security` wiring (greenfield — no legacy security to migrate) |
| 6 | **Test Specialist** | JUnit 5 slices covering the 26 coverage gaps from test-behavior analysis |

---

## 7. Concern-by-Concern Mapping

### 7a. Persistence

- **Legacy:** JPA 2.1 (`javax.persistence`), `@PersistenceContext` CDI producer, JNDI datasource, Hibernate HBM2DDL create-drop.
- **Target:** Jakarta Persistence 3.1 (`jakarta.persistence`), Spring Data JPA auto-configuration, JDBC datasource in properties, Flyway schema management.
- `MemberRepository`: implement as a Spring `@Repository` using `EntityManager` directly (faithful to the legacy Criteria API queries) or as a `JpaRepository` with custom query methods — Persistence Specialist's choice, but both query methods (`findAllOrderedByName`, `findByEmail`, `findById`) must be preserved with exact semantics.
- CDI `@Produces EntityManager` → remove; Spring injects `EntityManager` directly via `@PersistenceContext` (Jakarta) on the repository.

### 7b. Business Logic

- **Legacy:** `@Stateless` `MemberRegistration`, CDI `Event<Member>`, `@Observes Member` in `MemberListProducer`.
- **Target:** `@Service @Transactional MemberRegistration`, `ApplicationEventPublisher.publishEvent(new MemberRegisteredEvent(member))`, `@EventListener` on `MemberListProducer` equivalent — or merge `MemberListProducer` into the Thymeleaf controller since it is request-scoped UI glue.
- CDI event `Reception.IF_EXISTS` semantics: Spring `@EventListener` fires unconditionally; the equivalent guard is handled by Thymeleaf controller scope. Business Logic Specialist must document this change.
- `MemberController.initNewMember()` Gson diagnostic block: **deleted**. No replacement.
- `MemberControllerSecond.initNewMember()` DeltaSpike config read: migrated to `@Value("${config.key:Default value}") private String configKey;` injected into the UI controller.

### 7c. REST API

- **Legacy:** JAX-RS 2.0 (`javax.ws.rs`), two identical REST services at different context roots (`/kitchensink-ear-web/rest/members` and `/kitchensink-ear-web2/rest2/members`).
- **Target:** Spring MVC `@RestController`. Two controllers with different `@RequestMapping` prefixes preserve the dual-path contract.
  - `MemberRestController`: `@RequestMapping("/rest/members")`
  - `MemberRestControllerV2`: `@RequestMapping("/rest2/members")`
- **Exact HTTP contract preserved:**
  - `POST /members` success → `200 OK` (not 201) with empty body.
  - `POST /members` constraint violation → `400` with `{ "fieldName": "error message" }` JSON map.
  - `POST /members` duplicate email → `409 Conflict` with `{ "email": "Email taken" }`.
  - `GET /members/{id}` unknown id → `404 Not Found`.
  - Path constraint `[0-9][0-9]*` on `{id}` → `@PathVariable` with `@Pattern` or regex in `@RequestMapping`.
- `@ApplicationPath` activator → removed (no JAX-RS in target).
- Bean Validation wiring: `@Valid` on `@RequestBody Member`, `BindingResult` or `MethodArgumentNotValidException` handler replaces manual `Validator` injection.

### 7d. Frontend / UI

- **Legacy:** JSF 2.2, `.xhtml` Facelets, `@Model` (`@RequestScoped + @Named`) controllers, EL `#{members}`, `FacesContext` CDI producer.
- **Target:** Thymeleaf + Spring MVC `@Controller`. Facelets templates rewritten as Thymeleaf HTML5. `FacesContext` CDI producer removed; Thymeleaf accesses model via `Model` method parameter.
- Context roots: the two WARs had `/kitchensink-ear-web` and `/kitchensink-ear-web2`. In the single Spring Boot app, a `server.servlet.context-path` of `/` is used and the two UI paths become `/kitchensink-ear-web/` and `/kitchensink-ear-web2/` as `@RequestMapping` prefixes on the Thymeleaf controllers. This preserves URL compatibility.
- Session timeout: `server.servlet.session.timeout=30m` in `application.properties`.

### 7e. Logging

- **Legacy:** SLF4J 1.7.21 API (provided by container), CDI Logger `@Produces` pattern, `prefer-application-packages` for SLF4J/Log4j.
- **Target:** SLF4J 2.x backed by Logback (Spring Boot default, BOM-managed). CDI Logger producer removed. Use `LoggerFactory.getLogger(Foo.class)` directly, or Lombok `@Slf4j`.
- `prefer-application-packages` WebLogic descriptor: irrelevant in fat JAR (no classloader conflict possible).
- Log4j bridge (`slf4j-log4j12`) and `log4j:log4j` 1.2.17: removed.

### 7f. Security

- **Legacy:** No security configured.
- **Target:** Include `spring-boot-starter-security` in POM. Auto-configuration is present but authentication/authorization rules are greenfield — Security Specialist defines them. No legacy security config to migrate.

### 7g. Configuration / Properties

| Legacy source | Migration |
|---|---|
| `weblogic-application.xml` | Discard entirely |
| `weblogic.xml` (both WARs) | Discard entirely |
| `faces-config.xml` | Discard (no JSF) |
| `beans.xml` | Discard (Spring replaces CDI discovery) |
| `persistence.xml` | Replaced by Spring Boot JPA auto-config in `application.properties` |
| `test-ds.xml` (Arquillian) | Deleted with Arquillian |
| `META-INF/apache-deltaspike.properties` (`config.key=configuration value from deltaspike file`) | Migrated: `config.key=configuration value from deltaspike file` in `application.properties` |
| JNDI datasource `jdbc/SSA` | `spring.datasource.url`, `spring.datasource.username`, `spring.datasource.password` in `application.properties` |

---

## 8. Testing Strategy

- **Delete:** `MemberRegistrationIT.java` (Arquillian, JUnit 4) — cannot run on Java 21 outside a WildFly container.
- **Replace with Spring Boot Test slices:**
  - `@DataJpaTest` — `MemberRepositoryTest`: covers the 8 repository behaviours (findById found/not-found, findByEmail found/not-found, findAllOrderedByName).
  - `@WebMvcTest(MemberRestController.class)` — covers all 26 REST behaviour gaps (GET list, GET by id 200/404, POST 200/400/409, emailAlreadyExists true/false).
  - `@SpringBootTest` + `@AutoConfigureMockMvc` — one end-to-end happy-path smoke test (register → list).
  - `@ExtendWith(MockitoExtension.class)` unit tests for `MemberRegistration` service (mock `EntityManager` and `ApplicationEventPublisher`).
- All 26 coverage gaps from `reverse_en/test-behavior.md §5` must be covered.

---

## 9. Compatibility / Out-of-BOM

| Concern | Assessment |
|---|---|
| Spring Boot 3.4 + Java 21 | Fully supported. Java 21 is the recommended LTS for SB 3.x. |
| Hibernate 6.x (via SB BOM) | `@Email` moves to `jakarta.validation.constraints.Email`; `@NotEmpty` is HV extension (still works) or swap for `@NotBlank`. JPA Criteria API is API-compatible, namespace change only. |
| Flyway (via SB BOM) | Compatible with H2 2.x and Jakarta namespace. |
| H2 2.x (vs 1.4.193) | Breaking change in H2 2.x: `COLUMN_NAME` in `INFORMATION_SCHEMA` is now uppercase. `V1__init.sql` must use standard SQL DDL, not H2-specific syntax. |
| Gson 2.8.6 | **Removed** — no BOM concern. |
| DeltaSpike 1.8.2 | **Removed** — DeltaSpike is incompatible with Jakarta EE 10; removal is mandatory. |
| WebLogic Maven plugin | **Removed** from EAR POM — not applicable to Spring Boot fat JAR deployment. |
| `spring.framework` 4.3.9 declared but unused | **Delete** from POM properties — was never wired as a dependency. |
| Virtual threads (Java 21 Loom) | Available; enable with `spring.threads.virtual.enabled=true` in `application.properties` if desired. Not required for correctness. |
