# Dependency Analysis — kitchensink-ear

Static analysis only — no build executed.
Source: pom.xml files, deployment descriptors, Java source imports, and `CommonLibsEar.zip` inspection.

---

## 1. WebLogic-Specific Artifacts (Direct, Must Remove)

### 1.1 Deployment Descriptors

| File | What it does | Removal/Replacement need |
|---|---|---|
| `ear/src/main/application/META-INF/weblogic-application.xml` | EAR-level: references shared library `CommonLibsWarForEar` (v1.0), configures in-memory session persistence, enables cross-WAR session sharing, overrides classloading for `org.slf4j.*` and `log4j.*` | Delete entirely. Replace session config with Spring Session or no-op (if sharing not needed). Replace classloading overrides with explicit Maven exclusions. |
| `web/src/main/webapp/WEB-INF/weblogic.xml` | Web-level: sets UTF-8 charset, JSP keepgenerated/debug, context-root `/kitchensink-ear-web` | Delete. Context-root → `server.servlet.context-path` in `application.properties`. JSP settings → irrelevant (JSF → Thymeleaf). |
| `web2/src/main/webapp/WEB-INF/weblogic.xml` | Same as above plus `show-archived-real-path-enabled=true` | Delete. Flag is WebLogic-internal, no Spring Boot equivalent or need. |

### 1.2 WebLogic Maven Plugin

| Artifact | Location | Reference | Removal/Replacement need |
|---|---|---|---|
| `com.oracle.weblogic:weblogic-maven-plugin:12.2.1-3-0` | `ear/pom.xml`, profile `auto-deploy` | Deploys EAR to `t3://localhost:7003` via T3 protocol | Remove the entire `auto-deploy` profile. Replace with `spring-boot:run` or container-image deployment. |

### 1.3 Shared Library: `CommonLibsEar.zip` / `CommonLibsWarForEar`

Referenced in `weblogic-application.xml` as a WebLogic shared library installed on the server.
The zip (committed to the repo root, ~1 MB) was inspected and contains:

| Entry | Notes |
|---|---|
| `CommonLibsEar/CommonLibsWarForEar.war` | Empty WAR (just `WEB-INF/web.xml`); the WAR itself carries no classes |
| `CommonLibsEar/lib/deltaspike-core-api-1.8.2.jar` | CDI extension, Java EE 7 / `javax.*` |
| `CommonLibsEar/lib/deltaspike-core-impl-1.8.2.jar` | CDI extension implementation |
| `CommonLibsEar/lib/gson-2.8.6.jar` | JSON serialization |
| `CommonLibsEar/lib/log4j-1.2.17.jar` | Logging implementation (EOL) |
| `CommonLibsEar/lib/slf4j-api-1.7.21.jar` | SLF4J facade |
| `CommonLibsEar/lib/slf4j-log4j12-1.7.21.jar` | SLF4J → Log4j 1.x bridge |
| `CommonLibsEar/META-INF/weblogic-application.xml` | Empty WebLogic descriptor |

**Removal/Replacement:** Delete `CommonLibsEar.zip`. All jars it provided must be declared as explicit Maven dependencies, with updated versions (see §3). The shared-library deployment mechanism does not exist in Spring Boot.

---

## 2. Java EE API Dependencies (Direct, javax.* namespace — full migration required)

**Namespace finding:** All 14 production Java source files use the `javax.*` namespace exclusively. Zero `jakarta.*` usages. A complete package rename is required for Spring Boot 3.x / Jakarta EE 10.

### 2.1 Source-level imports by API

| API | `javax.*` packages used | Source files | Jakarta EE 10 replacement |
|---|---|---|---|
| CDI | `javax.enterprise.context.*`, `javax.enterprise.event.*`, `javax.enterprise.inject.*`, `javax.inject.*` | ejb (3 files), web (3 files), web2 (3 files) | `jakarta.enterprise.*`, `jakarta.inject.*` — Spring Boot `spring-boot-starter` includes these |
| EJB | `javax.ejb.Stateless` | `MemberRegistration.java` | Remove; replace with Spring `@Service`. JTA via Spring `@Transactional`. |
| JPA | `javax.persistence.*` | `Member.java`, `MemberRepository.java`, `Resources.java` | `jakarta.persistence.*` via `spring-boot-starter-data-jpa` |
| Bean Validation | `javax.validation.*` | `Member.java`, REST services (web + web2) | `jakarta.validation.*` via `spring-boot-starter-validation` |
| JAX-RS | `javax.ws.rs.*` | `JaxRsActivator.java`, REST services (web + web2) | Replace with Spring MVC `@RestController`/`@RequestMapping`; or keep JAX-RS via `spring-boot-starter-jersey` |
| JSF | `javax.faces.*` | `MemberController.java` (web + web2), `WebResources.java` (web) | Remove entirely; replace with Thymeleaf + `spring-boot-starter-thymeleaf` |
| JAXB | `javax.xml.bind.annotation.XmlRootElement` | REST services | `jakarta.xml.bind.*` (or switch to Jackson) |
| Common Annotations | `javax.annotation.PostConstruct` | `MemberListProducer.java`, `MemberController.java` | `jakarta.annotation.PostConstruct` |

### 2.2 Provided-scope Maven artifacts

| groupId:artifactId | Version | Module | Replacement |
|---|---|---|---|
| `javax.enterprise:cdi-api` | via BOM | ejb, web, web2 | `jakarta.enterprise:jakarta.enterprise.cdi-api` (managed by Spring Boot BOM) |
| `org.hibernate.javax.persistence:hibernate-jpa-2.1-api` | via BOM | ejb, web, web2 | `jakarta.persistence:jakarta.persistence-api` (via `spring-boot-starter-data-jpa`) |
| `org.hibernate:hibernate-validator` | via BOM (5.x era) | ejb, web, web2 | Hibernate Validator 8.x (via `spring-boot-starter-validation`) |
| `org.jboss.spec.javax.ejb:jboss-ejb-api_3.2_spec` | via BOM | ejb | Remove; no EJB in Spring Boot. |
| `org.jboss.spec.javax.faces:jboss-jsf-api_2.2_spec` | via BOM | web, web2 | Remove; replace UI layer with Thymeleaf. |
| `org.jboss.spec.javax.ws.rs:jboss-jaxrs-api_2.0_spec` | via BOM | web, web2 | Remove; use Spring MVC or Jersey starter. |

### 2.3 BOM imports (must remove)

| BOM | Version | Replacement |
|---|---|---|
| `org.wildfly.bom:wildfly-javaee7-with-tools` | `11.0.0.CR1` | Spring Boot parent BOM (`spring-boot-starter-parent:3.4.x`) |
| `org.jboss.spec:jboss-javaee-7.0` | `1.1.0.Final` | Same — Spring Boot BOM already manages Jakarta EE 10 APIs |

---

## 3. JNDI Datasource (Direct, Must Migrate)

| Descriptor | JNDI name | Where used | Replacement |
|---|---|---|---|
| `ejb/src/main/resources/META-INF/persistence.xml` | `jdbc/SSA` | `<jta-data-source>jdbc/SSA</jta-data-source>` | Remove `<jta-data-source>`. Configure `spring.datasource.*` in `application.properties`. Replace Hibernate `create-drop` DDL with Flyway or Liquibase. |

---

## 4. Third-party Dependencies with Migration Implications

### 4.1 DeltaSpike CDI Extension (High risk)

| Artifact | Version | Declared in | Risk |
|---|---|---|---|
| `org.apache.deltaspike.core:deltaspike-core-api` | 1.8.2 | `ear/pom.xml` (provided), `web/pom.xml` (provided), `web2/pom.xml` (provided); also bundled in `CommonLibsEar.zip` | DeltaSpike 1.8.2 is compiled against CDI 1.2 (`javax.*`) and has no Spring Boot equivalent. CDI extension SPIs it exposes (config resolution, bean decorating) must be re-implemented using Spring mechanisms. |
| `org.apache.deltaspike.core:deltaspike-core-impl` | 1.8.2 | Same | Same |

**Usage in source:** `com.google.gson.Gson` is injected in `MemberController.java` (`web`); no DeltaSpike API is imported directly in the scanned source files — DeltaSpike may be used only at the container level (CDI extension lifecycle). Requires runtime verification.

### 4.2 Logging Stack (Log4j 1.x — EOL, CVEs)

| Artifact | Version | Where | Risk / Replacement |
|---|---|---|---|
| `log4j:log4j` | 1.2.17 | `ear/pom.xml` (provided); `CommonLibsEar.zip/lib/` | End-of-life; multiple CVEs. Remove. Spring Boot autoconfigures SLF4J + Logback. |
| `org.slf4j:slf4j-log4j12` | 1.7.21 | `ear/pom.xml`, `ejb/pom.xml`, `web/pom.xml`; `CommonLibsEar.zip/lib/` | Bridges SLF4J to Log4j 1.x — remove with log4j. |
| `org.slf4j:slf4j-api` | 1.7.21 | All modules + CommonLibsEar | Upgrade to 2.x (Spring Boot 3 manages this). |

WebLogic `prefer-application-packages` in `weblogic-application.xml` was needed to prevent WL's own SLF4J from shadowing the app's. That conflict disappears in Spring Boot.

### 4.3 Gson (bundled in shared lib)

| Artifact | Version | Usage | Migration note |
|---|---|---|---|
| `com.google.code.gson:gson` | 2.8.6 | `ear/pom.xml` (provided); `MemberController.java` uses `new Gson()` directly | Compatible with Spring Boot; bump to 2.10.x (CVE fixes). Jackson is Spring Boot's default — consider consolidating. |

### 4.4 Other Third-party (low coupling to WebLogic)

| Artifact | Version | Notes |
|---|---|---|
| `commons-io:commons-io` | 2.5 | Safe; upgrade to 2.15+ for CVE fixes. |
| `org.apache.commons:commons-lang3` | 3.5 | Safe; upgrade to 3.14+. |
| `org.apache.httpcomponents:httpclient` | 4.5.3 | HttpClient 4.x is EOL. Upgrade to HttpClient 5.x or use Spring's `RestClient` / `WebClient`. |
| `commons-logging:commons-logging` | 1.2 | Managed in root POM; Spring Boot excludes this in favour of jcl-over-slf4j. |

### 4.5 Test dependencies

| Artifact | Version | Replacement |
|---|---|---|
| `junit:junit` | 4.12 | JUnit 5 + `spring-boot-starter-test` |
| `org.jboss.arquillian.*` | via BOM | Remove; replace `MemberRegistrationIT.java` with `@SpringBootTest` integration test |

---

## 5. Cross-WAR Session Sharing

`weblogic-application.xml` enables `<sharing-enabled>true</sharing-enabled>` — sessions are shared between `kitchensink-ear-web` and `kitchensink-ear-web2`. This is a WebLogic runtime feature with no direct Spring Boot equivalent. Options: Spring Session with a shared store (Redis/JDBC), or merge the two WARs into one Spring Boot application (preferred for a flat-jar target).

---

## 6. Not Verified / Limitations

1. **Transitive BOM resolution not confirmed.** Without running `mvn dependency:tree`, the exact resolved versions pulled by `wildfly-javaee7-with-tools` and `jboss-javaee-7.0` are not confirmed. All provided-scope versions labeled "via BOM" above are estimates based on BOM coordinates.
2. **DeltaSpike internal javax.* usage not bytecode-verified.** Only the JAR manifests were read. The classes inside `deltaspike-core-api-1.8.2.jar` and `-impl-` almost certainly reference `javax.enterprise.*` directly (DeltaSpike 1.8.2 pre-dates Jakarta EE), but decompilation was not performed.
3. **httpclient 4.5.3 transitive graph unverified.** Its transitive deps (commons-codec, httpclient-cache, etc.) were not resolved.
4. **CommonLibsWarForEar.war is an empty WAR.** Only `WEB-INF/web.xml` was present inside. However, the WebLogic shared library mechanism can add resources at runtime that are not in the committed zip — it is possible that additional classes are present only on the WebLogic server and not captured here.
5. **Arquillian test wiring not traced.** `MemberRegistrationIT.java` uses Arquillian; its container adapter and transitive server-specific jars are not enumerated.
6. **No `javax.transaction.*` import found in source.** JTA is used implicitly via `@Stateless` EJB. The underlying `javax.transaction.*` dependency is provided by the container BOM and resolved at build time — not explicitly in any pom.xml scanned.
7. **Spring Framework 4.3.9 property exists** in root `pom.xml` (`version.spring.framework=4.3.9.RELEASE`) but is not wired to any dependency in the scanned POMs — likely a leftover property. Verify it is unused before deleting.
