# Dependency Analysis — kitchensink-ear

> Static analysis only. No build was executed. Generated: 2026-09-01 · Issue #7 / loop-1

---

## 1. WebLogic-Specific Bindings

### 1.1 Descriptor Files (must all be deleted)

| File | What it configures | Migration action |
|------|--------------------|------------------|
| `ear/src/main/application/META-INF/weblogic-application.xml` | Shared library ref `CommonLibsWarForEar`; session persistence=`memory`; session sharing enabled; classloading preference for `org.slf4j.*` and `log4j.*` | Delete; replace shared-library mechanism with standard Maven dependency; configure Spring Session if sharing is needed |
| `web/src/main/webapp/WEB-INF/weblogic.xml` | Charset UTF-8 on `/*`; JSP debug=true, keepgenerated=true; context-root `kitchensink-ear-web` | Delete; set charset filter / JSP settings in `application.properties`; context-root via `server.servlet.context-path` |
| `web2/src/main/webapp/WEB-INF/weblogic.xml` | Same as above + `show-archived-real-path-enabled`; context-root `kitchensink-ear-web2` | Delete; web2 becomes a separate Spring Boot app or a second `DispatcherServlet` registration |

### 1.2 WebLogic Maven Plugin (must be removed)

`com.oracle.weblogic:weblogic-maven-plugin:12.2.1-3-0` declared in `ear/pom.xml` under the `auto-deploy` profile. Also declares WebLogic admin connection properties (`t3://localhost:7003`, `weblogic`/`weblogic1`). Remove the profile and the property block.

### 1.3 WebLogic Shared Library — `CommonLibsEar.zip`

`weblogic-application.xml` references the shared library by name `CommonLibsWarForEar`. The zip (committed at repo root, ~1 MB) is the deployable shared library:

```
CommonLibsEar/CommonLibsWarForEar.war    ← library container (empty WAR body)
CommonLibsEar/lib/
  deltaspike-core-api-1.8.2.jar
  deltaspike-core-impl-1.8.2.jar
  gson-2.8.6.jar
  log4j-1.2.17.jar
  slf4j-api-1.7.21.jar
  slf4j-log4j12-1.7.21.jar
CommonLibsEar/META-INF/MANIFEST.MF      Extension-Name: CommonLibsWarForEar
CommonLibsEar/META-INF/weblogic-application.xml  (empty, namespace only)
CommonLibsEar/META-INF/application.xml
```

**Migration action:** Delete `CommonLibsEar.zip` from the repo. Every jar it contains must be added as an explicit Maven compile-scope dependency (or replaced — see §4). No WebLogic class references were found inside any bundled jar (clean scan).

---

## 2. `javax.*` vs `jakarta.*` Namespace

**All** source imports use the `javax.*` namespace (Java EE 7). There are **zero** `jakarta.*` imports. Spring Boot 3.x / Jakarta EE 10 requires `jakarta.*`. Full namespace migration is mandatory.

| `javax.*` package in use | Jakarta EE 10 replacement |
|--------------------------|---------------------------|
| `javax.annotation.*` | `jakarta.annotation.*` |
| `javax.ejb.*` | `jakarta.ejb.*` (or refactor to Spring `@Service`) |
| `javax.enterprise.*` (CDI) | `jakarta.enterprise.*` (or refactor to Spring beans) |
| `javax.faces.*` (JSF) | `jakarta.faces.*` (PrimeFaces 14 / MyFaces 4) or replace with Thymeleaf |
| `javax.inject.*` | `jakarta.inject.*` (or use Spring `@Autowired`) |
| `javax.persistence.*` (JPA 2.1) | `jakarta.persistence.*` (JPA 3.x via Spring Data JPA) |
| `javax.validation.*` (BV 1.x) | `jakarta.validation.*` (BV 3.x) |
| `javax.ws.rs.*` (JAX-RS 2.0) | Replace with Spring MVC `@RestController` |
| `javax.xml.bind.*` (JAXB) | `jakarta.xml.bind.*` (add `jakarta.xml.bind-api` + implementation) |

---

## 3. Direct Maven Dependencies Requiring Action

### 3.1 Java EE BOM (`provided` scope)

The root POM imports `org.wildfly.bom:wildfly-javaee7-with-tools:11.0.0.CR1` and `org.jboss.spec:jboss-javaee-7.0:1.1.0.Final`. Both are WildFly/JBoss-specific APIs provided by the container; in Spring Boot they become explicit Jakarta EE dependencies or Spring equivalents.

### 3.2 CDI API — `javax.enterprise:cdi-api` (`provided`)

Used throughout. Replace with `jakarta.enterprise:jakarta.enterprise.cdi-api` (for `@Inject`, `@Named`, `@Produces`) or refactor injection to Spring `@Autowired` / `@Component`.

### 3.3 EJB API — `org.jboss.spec.javax.ejb:jboss-ejb-api_3.2_spec` (`provided`)

`MemberRegistration` is `@Stateless`. In Spring Boot: replace with `@Service` + `@Transactional`.

### 3.4 JSF API — `org.jboss.spec.javax.faces:jboss-jsf-api_2.2_spec` (`provided`)

Used by `MemberController`, `MemberControllerSecond`, `WebResources`. Spring Boot has no built-in JSF. Options: embed PrimeFaces/MyFaces 4 (Jakarta) or replace UI layer with Thymeleaf + Spring MVC.

### 3.5 JAX-RS API — `org.jboss.spec.javax.ws.rs:jboss-jaxrs-api_2.0_spec` (`provided`)

Used by `MemberResourceRESTService` and `MemberResourceRESTServiceSecond`. Replace with `spring-boot-starter-web` (`@RestController`, `@GetMapping`, `@PostMapping`).

### 3.6 Hibernate Validator — `org.hibernate:hibernate-validator` (`provided`)

**Two legacy annotations removed in Hibernate Validator 6.x / 8.x:**

| Location | Legacy annotation | Replacement |
|----------|-------------------|-------------|
| `ejb/src/main/java/.../model/Member.java:33` | `org.hibernate.validator.constraints.Email` | `jakarta.validation.constraints.Email` |
| `ejb/src/main/java/.../model/Member.java:34` | `org.hibernate.validator.constraints.NotEmpty` | `jakarta.validation.constraints.NotBlank` |

Spring Boot 3.4 bundles Hibernate Validator 8.x. These annotations **will not compile** without replacement.

### 3.7 JPA API — `org.hibernate.javax.persistence:hibernate-jpa-2.1-api` (`provided`)

Replace with `jakarta.persistence:jakarta.persistence-api:3.1` (pulled in automatically by `spring-boot-starter-data-jpa`).

### 3.8 SLF4J — `org.slf4j:slf4j-api:1.7.21` (`provided`, all modules)

SLF4J 1.x. Spring Boot 3.x bundles SLF4J 2.x (API-compatible). Remove explicit declarations and let Spring Boot BOM manage the version.

---

## 4. Indirect / Third-Party Dependencies Requiring Replacement

### 4.1 Apache DeltaSpike — **HIGH MIGRATION RISK**

| Artifact | Version | Scope | Usage |
|----------|---------|-------|-------|
| `deltaspike-core-api` | 1.8.2 | provided (web, web2, ear) | `ConfigResolver` called at runtime |
| `deltaspike-core-impl` | 1.8.2 | provided (web, web2, ear) | CDI extension implementation |

**Active usage confirmed:** `web2/src/main/java/.../controller/MemberControllerSecond.java:27,75`

```java
import org.apache.deltaspike.core.api.config.ConfigResolver;
// ...
String configValue = ConfigResolver.getPropertyValue("config.key", "Default value");
```

DeltaSpike is a CDI extension framework. **No Jakarta EE 10 / Spring Boot compatible release exists.** Must be replaced with Spring's `@Value("${config.key:Default value}")` or `Environment.getProperty(...)`.

Also shipped inside `CommonLibsEar.zip` — confirms runtime dependency through the WebLogic shared library mechanism.

### 4.2 Log4j 1.x — **SECURITY RISK**

| Artifact | Version | Source |
|----------|---------|--------|
| `log4j:log4j:1.2.17` | 1.2.17 | `ear/pom.xml` (`provided`) + `CommonLibsEar.zip/lib/` |
| `org.slf4j:slf4j-log4j12:1.7.21` | 1.7.21 | `ear/pom.xml` (`provided`) + `CommonLibsEar.zip/lib/` |

Log4j 1.x reached End-of-Life in 2015 and has known CVEs (including RCE via SocketServer). Spring Boot 3.x defaults to Logback. Remove `log4j` and `slf4j-log4j12`; remove `log4j.xml` configuration files from `web/` and `web2/`; configure Logback via `logback-spring.xml` or `application.properties`.

### 4.3 Gson — `com.google.code.gson:gson:2.8.6`

Bundled via shared library and declared in `ear/pom.xml`. Gson 2.8.6 has known deserialization vulnerabilities. If Gson is retained (e.g. for JSON serialization in REST layer), upgrade to 2.10.1+; alternatively, Jackson (already included via Spring Boot's `spring-boot-starter-web`) handles the same use cases.

### 4.4 Arquillian — Test Framework

| Artifact | Scope |
|----------|-------|
| `org.jboss.arquillian.junit:arquillian-junit-container` | test |
| `org.jboss.arquillian.protocol:arquillian-protocol-servlet` | test |
| `org.jboss.shrinkwrap.*` (transitive) | test |

Used in `ejb/src/test/java/.../MemberRegistrationIT.java`. Arquillian deploys archives to a live container; incompatible with Spring Boot testing. Replace with `@SpringBootTest` + Testcontainers or an H2 in-memory database for integration tests.

### 4.5 Apache HttpClient — `org.apache.httpcomponents:httpclient:4.5.3`

Declared in the root BOM's `dependencyManagement` but **not imported by any module**. No `import` statements found. Likely a vestigial entry. Safe to drop; if HTTP client calls are needed, use `RestClient` (Spring Boot 3.2+).

---

## 5. Persistence Configuration

- **JNDI datasource:** `jdbc/SSA` declared in `ejb/src/main/resources/META-INF/persistence.xml`. In Spring Boot, replace with `spring.datasource.*` in `application.properties`; JNDI lookup is not available by default.
- **Hibernate DDL:** `hibernate.hbm2ddl.auto=create-drop` — retain for dev/test; for production, switch to Flyway or Liquibase.
- **Test datasource:** H2 in-memory (`jdbc:h2:mem:kitchensink-ear-quickstart-test`) via JBoss `datasources` schema XML (`test-ds.xml`). Replace with H2 auto-configuration in Spring Boot test properties.
- **Table:** `AA_Registrant` (via `@Table(name="AA_Registrant")`). No schema change required; preserve.

---

## 6. Multi-Module EAR → Spring Boot Restructuring

The current packaging is a Java EE EAR (1 EJB JAR + 2 WARs + shared library). Spring Boot does not support EAR packaging. Target structure options:

| Option | Description |
|--------|-------------|
| **Single Spring Boot app** | Merge `ejb` + `web` + `web2` into one Spring Boot module; the two WARs become two sets of controllers under different URL prefixes |
| **Two Spring Boot apps** | One per WAR; shared `ejb` module becomes a library JAR; suitable if different deployment lifecycles are needed |

`web` and `web2` are structurally identical (`*Second` naming convention). Deduplication opportunity exists at the architecture stage.

---

## 7. Spring Framework — Vestigial Property

`version.spring.framework=4.3.9.RELEASE` is defined in root `pom.xml` properties but **no module declares a Spring dependency**. This property is unused. Safe to remove; do not interpret as an active Spring 4 dependency.

---

## 8. CDI Descriptor — `beans.xml` (bean-discovery-mode="all")

`beans.xml` files in `ejb/`, `web/`, and `web2/` with `bean-discovery-mode="all"`. Not needed in Spring Boot; CDI is replaced by Spring IoC. These files can be deleted after migration.

---

## 9. Suspects — Indirect / Unverifiable Without Build

| Library | Risk | Reason |
|---------|------|--------|
| Transitive deps of `deltaspike-core-impl:1.8.2` | Medium | May pull additional CDI-specific jars not visible statically |
| Transitive deps of `wildfly-javaee7-with-tools` BOM | Medium | BOM may activate additional JBoss modules at container boot |
| `CommonLibsWarForEar.war` inner contents | Low | Inner WAR body is empty (only `WEB-INF/web.xml`), but a build of the shared library might add classes not present in the committed zip |

---

## 10. Not Verified / Limitations

- **No Maven dependency resolution performed.** Transitive graph of `wildfly-javaee7-with-tools` BOM is not fully enumerated; additional javax.* APIs pulled transitively may exist.
- **No runtime class loading verified.** WebLogic `prefer-application-packages` for `org.slf4j.*` and `log4j.*` affects classloader order at runtime — static analysis cannot reproduce this.
- **`CommonLibsEar.zip` jar scan is shallow.** Inner jars were scanned for `weblogic`/`oracle` class references in their `ZipEntry` names only; bytecode-level references (string literals, reflection) were not checked.
- **Arquillian deployment descriptors** (`arquillian.xml`) reference a managed container configuration that requires a running WildFly instance — no way to determine which additional libraries are injected into the test deployment beyond what `ShrinkWrap` packages.
- **JNDI datasource binding.** The production datasource (`jdbc/SSA`) target server, driver, and credentials are not in the repo — the actual database type (Oracle, DB2, etc.) is unknown and may impose additional driver dependencies.
