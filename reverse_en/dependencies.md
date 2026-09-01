# Dependency Analysis — kitchensink-ear

**Source**: `original_app/` | **Target**: Java 21 / Spring Boot 3.4 / Jakarta EE namespace  
**Method**: Static reading only — no build executed.  
**Issue**: #9 | **Loop**: loop-1 | **Date**: 2026-09-01

---

## 1. WebLogic Deployment Descriptors

| File | Contents | Migration action |
|------|----------|-----------------|
| `ear/src/main/application/META-INF/weblogic-application.xml` | Shared-library ref (`CommonLibsWarForEar 1.0/1.0`); session `persistent-store-type=memory`, `sharing-enabled=true`; `prefer-application-packages` for `org.slf4j.*` and `log4j.*` | **Delete entirely.** Spring Boot requires none of this. Session config moves to `application.properties`. |
| `web/src/main/webapp/WEB-INF/weblogic.xml` | Charset encoding, JSP debug flags, context-root `kitchensink-ear-web` | **Delete.** Context-root set in `application.properties` (`server.servlet.context-path`). |
| `web2/src/main/webapp/WEB-INF/weblogic.xml` | Same as web; `show-archived-real-path-enabled=true` | **Delete.** web2 module merges into a single Spring Boot app. |

## 2. WebLogic Maven Plugin (ear/pom.xml — `auto-deploy` profile)

```
com.oracle.weblogic:weblogic-maven-plugin:12.2.1-3-0
```
- Used to `redeploy` the EAR to a local WebLogic admin server via T3 (`t3://localhost:7003`).
- Hardcoded credentials in pom.xml (`weblogic`/`weblogic1`).
- **Remove** the entire `auto-deploy` profile. Spring Boot is deployed as a fat JAR; no server plugin needed.

## 3. WebLogic Shared Library — CommonLibsWarForEar

- Referenced in `weblogic-application.xml`: `CommonLibsWarForEar 1.0/1.0`.
- Binary archive `original_app/CommonLibsEar.zip` (1.1 MB) is committed in the repo but **not a valid zip** (`unzip -l` returns no output; `file` reports it as data, not an archive). Contents cannot be statically inspected.
- **Risk**: Unknown transitive dependencies are bundled here. All dependencies it provided must be identified at runtime or by the platform team before migration can be marked complete.
- **Action**: Platform team must inventory `CommonLibsWarForEar` contents; any library it contributes must be added explicitly to the Spring Boot `pom.xml`.

## 4. javax.* → jakarta.* Namespace (Full Inventory)

All source files use `javax.*`. Every import below must be renamed to `jakarta.*` for Spring Boot 3.x / Jakarta EE 10:

| javax.* package | jakarta.* replacement | Affected module |
|----------------|-----------------------|----------------|
| `javax.annotation.PostConstruct` | `jakarta.annotation.PostConstruct` | ejb, web, web2 |
| `javax.ejb.Stateless` | Remove (replace with `@Service` + `@Transactional`) | ejb |
| `javax.enterprise.context.*` | Remove (replace with Spring scopes) | ejb, web, web2 |
| `javax.enterprise.event.*` | Remove (replace with Spring `ApplicationEvent`) | ejb |
| `javax.enterprise.inject.*` | Remove (replace with `@Autowired`, `@Bean`) | ejb, web, web2 |
| `javax.faces.*` | Remove (JSF dropped; replace with Spring MVC / Thymeleaf) | web, web2 |
| `javax.inject.Inject` | Remove (use `@Autowired`) | ejb, web, web2 |
| `javax.inject.Named` | Remove (use `@Component` / `@Bean`) | ejb, web, web2 |
| `javax.persistence.*` | `jakarta.persistence.*` | ejb |
| `javax.validation.constraints.*` | `jakarta.validation.constraints.*` | ejb |
| `javax.validation.*` | `jakarta.validation.*` | ejb, web, web2 |
| `javax.ws.rs.*` | Remove (replace with Spring MVC `@RestController`) | web, web2 |
| `javax.xml.bind.annotation.XmlRootElement` | `jakarta.xml.bind.annotation.XmlRootElement` or replace with `@JsonRootName` | ejb |

## 5. Direct Dependencies Requiring Replacement

### 5a. DeltaSpike 1.8.2 (**High risk**)
- **Artifacts**: `deltaspike-core-api`, `deltaspike-core-impl` (web, web2, ear poms; `provided` scope)
- **Active usage**: `MemberControllerSecond.java` calls `ConfigResolver.getPropertyValue("config.key", "Default value")` with config sourced from `web2/src/main/resources/META-INF/apache-deltaspike.properties`
- **Replacement**: Spring `@Value("${config.key:Default value}")` or `Environment.getProperty(...)`. The `apache-deltaspike.properties` content moves to `application.properties`.
- **Risk**: DeltaSpike is a CDI extension — incompatible with Spring Boot. Remove entirely.

### 5b. Hibernate Validator legacy constraints (**Blocker**)
- **Artifact**: `org.hibernate:hibernate-validator` (provided; resolved via `wildfly-javaee7-with-tools` BOM)
- **Active usage in `Member.java`**:
  - `import org.hibernate.validator.constraints.Email;` → annotation `@Email` on `email` field
  - `import org.hibernate.validator.constraints.NotEmpty;` → annotation `@NotEmpty` on `email` field
- **Problem**: `org.hibernate.validator.constraints.Email` and `NotEmpty` were **deprecated in HV 6.0** and **removed in HV 8.x** (which ships with Spring Boot 3.x). These will cause a compile error.
- **Replacement**: `@jakarta.validation.constraints.Email` and `@jakarta.validation.constraints.NotBlank` (standard Bean Validation 3.0).

### 5c. Log4j 1.x + SLF4J-Log4j12 bridge (**Security/EOL blocker**)
- **Artifacts** (ear pom, `provided`): `log4j:log4j:1.2.17`, `org.slf4j:slf4j-log4j12:1.7.21`
- Log4j 1.x reached end-of-life in 2015; multiple known CVEs. Spring Boot 3.x ships Logback via `spring-boot-starter-logging`.
- **Replacement**: Remove both artifacts. Add `spring-boot-starter-logging` (Logback) or `log4j-spring-boot` (Log4j2). SLF4J facade remains; only the binding changes.
- **weblogic-application.xml** `prefer-application-packages` for `log4j.*` and `org.slf4j.*` is moot once the server is gone.

### 5d. SLF4J API 1.7.21
- **Artifact**: `org.slf4j:slf4j-api:1.7.21` (explicit in ejb, web, web2, ear poms; `provided`)
- Spring Boot 3.x requires SLF4J 2.x. Remove explicit declarations; use Spring Boot starter BOM.

### 5e. Gson 2.8.6
- **Artifact**: `com.google.code.gson:gson:2.8.6` (web, ear poms; `provided`)
- Not actively referenced in the scanned source (no `import com.google.gson` found). May be provided via `CommonLibsWarForEar`.
- **Action**: Verify whether any class uses Gson. If unused, remove. If used, upgrade to 2.10+ and add as an explicit `compile` dependency (Spring Boot 3.4 starter-web bundles Jackson by default; Gson is optional).

### 5f. Arquillian (test-scope)
- **Artifacts**: `arquillian-junit-container`, `arquillian-protocol-servlet` (ejb pom; `test`)
- Arquillian requires a running Java EE container. Not applicable to Spring Boot.
- **Replacement**: Spring Boot Test (`@SpringBootTest`, `@DataJpaTest`, MockMvc). Rewrite `MemberRegistrationIT`.

## 6. JBoss/WildFly-Specific Spec Artifacts

All are `provided` scope — supplied by WildFly at runtime. Must be replaced with Jakarta EE 10 standard equivalents bundled by Spring Boot starters.

| JBoss artifact | Spring Boot equivalent |
|---------------|----------------------|
| `org.jboss.spec.javax.ejb:jboss-ejb-api_3.2_spec` | Removed (EJB replaced by `@Service`/`@Transactional`) |
| `org.jboss.spec.javax.faces:jboss-jsf-api_2.2_spec` | Removed (JSF replaced by Thymeleaf or Spring MVC) |
| `org.jboss.spec.javax.ws.rs:jboss-jaxrs-api_2.0_spec` | Removed (JAX-RS replaced by Spring MVC `@RestController`) |
| `org.hibernate.javax.persistence:hibernate-jpa-2.1-api` | `jakarta.persistence-api` via `spring-boot-starter-data-jpa` |
| `org.wildfly.bom:wildfly-javaee7-with-tools` BOM | `spring-boot-starter-parent` BOM |
| `org.jboss.spec:jboss-javaee-7.0` BOM | `spring-boot-starter-parent` BOM |
| CDI API (`javax.enterprise:cdi-api`) | Spring IoC — `@Component`, `@Service`, `@Autowired` |

## 7. Persistence Layer

| Item | Legacy value | Migration action |
|------|-------------|-----------------|
| JNDI datasource | `jdbc/SSA` | Replace with `spring.datasource.*` in `application.properties`; actual DB driver TBD |
| JPA version | 2.1 (`javax.persistence`) | Migrate to Jakarta JPA 3.x (`jakarta.persistence`) |
| Schema tool | `hibernate.hbm2ddl.auto=create-drop` | Change to `validate` or `none` for production; use Flyway/Liquibase |
| Test datasource | H2 via `java:jboss/datasources/KitchensinkEarQuickstartTestDS` | Replace with H2 in-memory via `spring.datasource.url=jdbc:h2:mem:test` |

## 8. EAR → Spring Boot Module Restructuring

The original EAR packages one EJB JAR + two WARs. Spring Boot is a single executable JAR:
- `ejb` module → `service` package in Spring Boot app (EJB annotations removed)
- `web` + `web2` modules → merged into a single Spring Boot app with distinct controller packages
- EAR `lib/` directory (from maven-ear-plugin `defaultLibBundleDir=lib`) → explicit Maven dependencies
- `application.xml` → not needed (Spring Boot auto-configures)
- Context roots (`/kitchensink-ear-web`, `/kitchensink-ear-web2`) → single context-path or virtual path prefixes

## 9. Spring Framework 4.3.9 Reference in Root pom.xml

```xml
<version.spring.framework>4.3.9.RELEASE</version.spring.framework>
```
This version property is declared but not used in any module `pom.xml` dependency. It is likely a leftover or dead configuration. Do not carry it to the migration target; let `spring-boot-starter-parent` manage the Spring version.

## 10. Not Verified / Limitations

| Item | Why unverifiable statically |
|------|-----------------------------|
| `CommonLibsEar.zip` contents | File is not a valid ZIP archive — cannot unzip. All transitive deps it provides are unknown until platform team inspects the original WL server. |
| Actual JDBC driver for `jdbc/SSA` | JNDI-bound datasource; driver class and URL are server-side config, not in the repo. |
| Full transitive dep tree | `provided`-scope artifacts resolved by `wildfly-javaee7-with-tools` BOM (v11.0.0.CR1) are not on disk; only API-level imports are visible. |
| DeltaSpike module extensions | Only `deltaspike-core` is declared; other DeltaSpike modules (Security, JSF, etc.) may be contributed via `CommonLibsWarForEar`. |
| Log4j configuration | No `log4j.properties` or `log4j.xml` found in source tree; configuration may ship inside `CommonLibsEar.zip`. |
| Arquillian container under test | `MemberRegistrationIT` requires a running WildFly container; actual behavior under test is unverifiable without a build. |
