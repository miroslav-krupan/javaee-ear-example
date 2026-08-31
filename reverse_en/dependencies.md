# Dependency Analysis — kitchensink-ear (Issue #5 / loop-1)

Static analysis only — no build executed.
Sources: all `pom.xml` files, deployment descriptors, Java source imports, `CommonLibsEar.zip` (inspected with Python zipfile).

---

## 1. WebLogic-Specific Artifacts (Direct — Must Remove)

### 1.1 Deployment Descriptors

| File | What it configures | Replacement need |
|---|---|---|
| `ear/src/main/application/META-INF/weblogic-application.xml` | EAR-level: shared library ref `CommonLibsWarForEar` (spec 1.0 / impl 1.0, exact-match=false), in-memory session persistence, cross-WAR session sharing, classloading preference for `org.slf4j.*` and `log4j.*` | Delete. Session policy → Spring Session or stateless design. Classloading overrides → explicit Maven exclusions. |
| `web/src/main/webapp/WEB-INF/weblogic.xml` | WAR-level: UTF-8 charset, JSP keepgenerated/debug flags, context-root `/kitchensink-ear-web` | Delete. Context root → `server.servlet.context-path`. JSP settings → irrelevant (JSF being replaced). |
| `web2/src/main/webapp/WEB-INF/weblogic.xml` | Same as web, plus `show-archived-real-path-enabled=true` | Delete. Feature is WebLogic-internal with no Spring Boot equivalent. |

### 1.2 WebLogic Maven Plugin

| Artifact | Location | Binding | Replacement need |
|---|---|---|---|
| `com.oracle.weblogic:weblogic-maven-plugin:12.2.1-3-0` | `ear/pom.xml`, profile `auto-deploy`, phase `integration-test` | Deploys EAR over T3 to `t3://localhost:7003` | Remove the entire `auto-deploy` profile. Replace with `spring-boot:run` or Docker/Helm deployment. |

### 1.3 Shared Library: CommonLibsEar.zip / CommonLibsWarForEar

Referenced in `weblogic-application.xml` — this zip is committed at the repo root and was fully inspected.

**Contents of CommonLibsEar.zip:**

| Entry | Notes |
|---|---|
| `CommonLibsEar/CommonLibsWarForEar.war` | Empty WAR (only `WEB-INF/web.xml`) — carries no classes itself |
| `CommonLibsEar/lib/deltaspike-core-api-1.8.2.jar` | CDI extension, Java EE 7 / `javax.*` |
| `CommonLibsEar/lib/deltaspike-core-impl-1.8.2.jar` | CDI extension implementation |
| `CommonLibsEar/lib/gson-2.8.6.jar` | JSON serialization |
| `CommonLibsEar/lib/log4j-1.2.17.jar` | Logging impl (EOL) |
| `CommonLibsEar/lib/slf4j-api-1.7.21.jar` | SLF4J facade |
| `CommonLibsEar/lib/slf4j-log4j12-1.7.21.jar` | SLF4J → Log4j 1.x bridge |
| `CommonLibsEar/META-INF/weblogic-application.xml` | Empty WebLogic descriptor (body-only, no config) |

**Replacement:** Delete `CommonLibsEar.zip`. All jars it provided must become explicit Maven dependencies with current versions (see §4). The WebLogic shared-library loading mechanism does not exist in Spring Boot.

---

## 2. Java EE API Dependencies (Direct — javax.* namespace, full migration required)

**Namespace finding:** All 13 production Java source files use the `javax.*` namespace exclusively. Zero `jakarta.*` usages. A complete package rename is required for Spring Boot 3.x / Jakarta EE 10.

### 2.1 javax.* imports by API — source-level

| API | javax.* packages in use | Affected modules | Jakarta EE 10 / Spring Boot replacement |
|---|---|---|---|
| CDI | `javax.enterprise.context.*`, `javax.enterprise.event.*`, `javax.enterprise.inject.*`, `javax.inject.*` | ejb, web, web2 | `jakarta.enterprise.*`, `jakarta.inject.*` — included via `spring-boot-starter` |
| EJB | `javax.ejb.Stateless` | `MemberRegistration.java` (ejb) | Remove; replace with Spring `@Service`. Container JTA → Spring `@Transactional`. |
| JPA | `javax.persistence.*` | `Member.java`, `MemberRepository.java`, `Resources.java` (ejb) | `jakarta.persistence.*` via `spring-boot-starter-data-jpa` |
| Bean Validation | `javax.validation.*` | `Member.java`, REST services (web, web2) | `jakarta.validation.*` via `spring-boot-starter-validation` |
| JAX-RS | `javax.ws.rs.*` | `JaxRsActivator.java`, REST services (web, web2) | Spring MVC `@RestController` / `@RequestMapping`; or `spring-boot-starter-jersey` |
| JSF | `javax.faces.*` | `MemberController.java` (web), `MemberControllerSecond.java` (web2), `WebResources.java` (web, web2) | Remove; replace with Thymeleaf + `spring-boot-starter-thymeleaf` |
| JAXB | `javax.xml.bind.annotation.XmlRootElement` | `Member.java` | `jakarta.xml.bind.annotation.XmlRootElement`; or switch to Jackson `@JsonRootName` |
| Common Annotations | `javax.annotation.PostConstruct` | `MemberListProducer.java`, `MemberController.java`, `MemberControllerSecond.java` | `jakarta.annotation.PostConstruct` |

### 2.2 Legacy Hibernate Validator annotations (extra migration item — noted in repo-overview)

| Import | File | Issue | Replacement |
|---|---|---|---|
| `org.hibernate.validator.constraints.Email` | `Member.java` | Deprecated in HV 6.x, removed in HV 8.x | `jakarta.validation.constraints.Email` |
| `org.hibernate.validator.constraints.NotEmpty` | `Member.java` | Deprecated in HV 6.x, removed in HV 8.x | `jakarta.validation.constraints.NotBlank` or `@NotNull @Size(min=1)` |

### 2.3 Provided-scope Maven artifacts (declared in pom.xml files)

| groupId:artifactId | Ver | Module(s) | Replacement |
|---|---|---|---|
| `javax.enterprise:cdi-api` | via BOM | ejb, web, web2 | Managed by Spring Boot BOM (`jakarta.enterprise:jakarta.enterprise.cdi-api`) |
| `org.hibernate.javax.persistence:hibernate-jpa-2.1-api` | via BOM | ejb, web, web2 | `jakarta.persistence:jakarta.persistence-api` (via `spring-boot-starter-data-jpa`) |
| `org.hibernate:hibernate-validator` | via BOM (HV 5.x era) | ejb, web, web2 | Hibernate Validator 8.x (via `spring-boot-starter-validation`) |
| `org.jboss.spec.javax.ejb:jboss-ejb-api_3.2_spec` | via BOM | ejb | Remove — no EJB in Spring Boot |
| `org.jboss.spec.javax.faces:jboss-jsf-api_2.2_spec` | via BOM | web, web2 | Remove — replace with Thymeleaf |
| `org.jboss.spec.javax.ws.rs:jboss-jaxrs-api_2.0_spec` | via BOM | web, web2 | Remove — replace with Spring MVC or Jersey |

### 2.4 BOM imports (must remove entirely)

| BOM | Version | Replacement |
|---|---|---|
| `org.wildfly.bom:wildfly-javaee7-with-tools` | `11.0.0.CR1` | `spring-boot-starter-parent:3.4.x` (or `spring-boot-dependencies` BOM) |
| `org.jboss.spec:jboss-javaee-7.0` | `1.1.0.Final` | Same — Spring Boot BOM manages all Jakarta EE 10 API versions |

---

## 3. JNDI Datasource (Direct — Must Migrate)

| File | Reference | Replacement |
|---|---|---|
| `ejb/src/main/resources/META-INF/persistence.xml` | `<jta-data-source>jdbc/SSA</jta-data-source>` | Remove `<jta-data-source>`. Configure `spring.datasource.*` in `application.properties`. Replace `hibernate.hbm2ddl.auto=create-drop` with Flyway or Liquibase. |

---

## 4. Third-party Dependencies with Migration Implications

### 4.1 DeltaSpike CDI Extension (High risk — confirmed source-level usage)

| Artifact | Version | Declared in | Source usage |
|---|---|---|---|
| `org.apache.deltaspike.core:deltaspike-core-api` | 1.8.2 | `ear/pom.xml` (provided), `web/pom.xml` (provided), `web2/pom.xml` (provided); bundled in `CommonLibsEar.zip` | `MemberControllerSecond.java` (web2): `ConfigResolver.getPropertyValue("config.key", "Default value")` — actual call at line 75 |
| `org.apache.deltaspike.core:deltaspike-impl` | 1.8.2 | Same | CDI extension runtime — required for ConfigResolver to function |

**Migration impact:** `DeltaSpike ConfigResolver` is a CDI-based configuration resolver. In Spring Boot, replace with `@Value("${config.key:Default value}")` or `Environment.getProperty("config.key", "Default value")`. The CDI extension infrastructure (deltaspike-core-impl) is not applicable to Spring Boot and must be removed entirely. DeltaSpike 1.8.2 uses `javax.*` namespace and is incompatible with Spring Boot 3.x.

### 4.2 Gson (declared only in web; used only in web)

| Artifact | Version | Declared | Usage |
|---|---|---|---|
| `com.google.code.gson:gson` | 2.8.6 | `ear/pom.xml` (provided), `web/pom.xml` (provided) — **NOT in web2/pom.xml** | `MemberController.java` (web): `new Gson()` at line 75; web2 has no Gson usage |

**Note:** Gson is available to `web2` at runtime via the shared EAR lib (`ear/pom.xml` bundles it via CommonLibsEar), but it is not declared in `web2/pom.xml`. In Spring Boot, declare Gson explicitly where needed or switch to Jackson (Spring Boot default).

### 4.3 Logging stack (Log4j 1.x — EOL, CVEs present)

| Artifact | Version | Where | Replacement |
|---|---|---|---|
| `log4j:log4j` | 1.2.17 | `ear/pom.xml` (provided); `CommonLibsEar.zip/lib/` | Remove. Spring Boot uses SLF4J + Logback by default. |
| `org.slf4j:slf4j-log4j12` | 1.7.21 | `ear/pom.xml` (provided), `ejb/pom.xml` (provided), `web/pom.xml` (provided); `CommonLibsEar.zip/lib/` | Remove with log4j. |
| `org.slf4j:slf4j-api` | 1.7.21 | All modules + CommonLibsEar | Spring Boot 3 manages SLF4J 2.x — no explicit version needed. |

The `prefer-application-packages` override in `weblogic-application.xml` (for `org.slf4j.*`, `log4j.*`) resolves a WL classloader conflict that disappears in Spring Boot.

### 4.4 SLF4J usage in source

Source correctly uses `org.slf4j.Logger` / `org.slf4j.LoggerFactory` throughout — this API is stable across SLF4J 1.x→2.x and requires no source changes.

### 4.5 Other third-party (low WebLogic coupling)

| Artifact | Version | Notes |
|---|---|---|
| `commons-io:commons-io` | 2.5 | Declared in root dependencyManagement; upgrade to 2.15+ for CVE fixes. |
| `org.apache.commons:commons-lang3` | 3.5 | Declared in root dependencyManagement; upgrade to 3.14+. |
| `org.apache.httpcomponents:httpclient` | 4.5.3 | HttpClient 4.x is EOL. Upgrade to HttpClient 5.x or use Spring's `RestClient` / `WebClient`. |
| `commons-logging:commons-logging` | 1.2 | Declared in root dependencyManagement; Spring Boot excludes this in favour of `jcl-over-slf4j`. |

### 4.6 Spring Framework 4.3.9 property (orphan)

`version.spring.framework=4.3.9.RELEASE` is declared in root `pom.xml` properties, but is **not wired to any `<dependency>`** in any scanned POM. It is a dead property — confirm it is unused, then delete it to avoid confusion.

### 4.7 Test dependencies

| Artifact | Version | Replacement |
|---|---|---|
| `junit:junit` | 4.12 | JUnit 5 + `spring-boot-starter-test` |
| `org.jboss.arquillian.*` | via WildFly BOM | Remove. Replace `MemberRegistrationIT.java` with `@SpringBootTest` integration test. |

---

## 5. Cross-WAR Session Sharing

`weblogic-application.xml` enables `<sharing-enabled>true</sharing-enabled>` — HTTP sessions are shared between `kitchensink-ear-web` and `kitchensink-ear-web2`. This is a WebLogic runtime mechanism with no direct Spring Boot equivalent. Options: Spring Session with a shared store (Redis / JDBC), or consolidate the two WARs into one Spring Boot application (preferred for a flat-jar target given how similar `web` and `web2` are).

---

## 6. Not Verified / Limitations

1. **BOM transitive resolution not confirmed.** Without `mvn dependency:tree`, the exact resolved versions pulled by `wildfly-javaee7-with-tools` and `jboss-javaee-7.0` are not known. All "via BOM" version labels are estimates based on BOM coordinates and WildFly 11 era versions.
2. **DeltaSpike internal class-level javax.* references not bytecode-verified.** The JARs in `CommonLibsEar.zip` were inspected for entry names only; class-bytecode was not decompiled. DeltaSpike 1.8.2 is definitively pre-Jakarta but the exact `javax.*` surface was not enumerated class-by-class.
3. **httpclient 4.5.3 transitive graph unverified.** Commons-codec and other transitive deps not resolved.
4. **CommonLibsWarForEar.war is an empty WAR** (only `WEB-INF/web.xml` found). The WebLogic shared library mechanism can inject resources at runtime that are not in the committed zip — additional server-side content may exist in the target environment.
5. **Arquillian test classpath not traced.** `MemberRegistrationIT.java` uses Arquillian; its container adapter and server-specific transitive jars are not enumerated.
6. **`javax.transaction.*` not imported in source.** JTA is activated implicitly via `@Stateless` EJB. The underlying `javax.transaction.*` dependency is provided by the BOM and not explicit in any POM.
7. **`config.key` property source for DeltaSpike ConfigResolver** is unknown from static analysis — the property may be defined in a WebLogic config file, JNDI, or classpath resource not present in the repo.
