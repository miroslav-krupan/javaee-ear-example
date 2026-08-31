# Repository Overview — kitchensink-ear

## Summary

A multi-module Maven EAR application originally targeting **WebLogic 12.2.1-3-0**, built as a JBoss/WildFly quickstart but adapted for WebLogic deployment. The app demonstrates member registration using JSF, CDI, JAX-RS, EJB, JPA, and Bean Validation.

---

## Project Identity

| Property | Value |
|---|---|
| Root artifact | `org.wildfly.quickstarts:kitchensink-ear:11.0.0-SNAPSHOT` |
| Build tool | Maven (version unspecified; compiler plugin 3.8.0) |
| Java source / target | **Java 8** |
| Java EE version | **Java EE 7** (`wildfly-javaee7-with-tools` BOM, `jboss-javaee-7.0` BOM) |
| Packaging | **EAR** |
| Target runtime | **WebLogic 12.2.1-3-0** (weblogic-maven-plugin for auto-deploy profile) |

---

## Module Structure

```
kitchensink-ear/                  (root POM, packaging=pom)
├── ejb/                          (kitchensink-ear-ejb, packaging=ejb, EJB 3.0)
├── web/                          (kitchensink-ear-web, packaging=war)
├── web2/                         (kitchensink-ear-web2, packaging=war)
└── ear/                          (kitchensink-ear-ear, packaging=ear — assembles all)
```

### ejb module
- **Role**: Business logic and data access layer
- **Key classes**:
  - `model/Member.java` — JPA entity, table `AA_Registrant`, fields: id, name, email, phoneNumber
  - `data/MemberRepository.java` — JPA repository (CDI bean)
  - `data/MemberListProducer.java` — CDI producer for member list
  - `service/MemberRegistration.java` — Stateless EJB, JTA transaction management
  - `util/Resources.java` — CDI producers (EntityManager, Logger)
- **Persistence**: JPA 2.1, JNDI datasource `jdbc/SSA`, `hbm2ddl.auto=create-drop`
- **Tests**: Arquillian integration test (`MemberRegistrationIT.java`)

### web module
- **Role**: Primary web tier
- **Context root**: `/kitchensink-ear-web`
- **Technologies**: JSF (faces-config.xml), CDI (beans.xml), JAX-RS
- **Key classes**:
  - `rest/MemberResourceRESTService.java` — JAX-RS resource: `GET /members`, `GET /members/{id}`, `POST /members`
  - `rest/JaxRsActivator.java` — JAX-RS application activator (`@ApplicationPath("/rest")`)
  - `controller/MemberController.java` — JSF backing bean
  - `util/WebResources.java` — CDI producers for JSF
- **WebLogic descriptor**: `weblogic.xml` — charset UTF-8, JSP debug, context root

### web2 module
- **Role**: Secondary web tier (mirrors web with slightly different REST service)
- **Context root**: `/kitchensink-ear-web2`
- **Technologies**: JSF, CDI, JAX-RS (same stack as web)
- **Key class**: `rest/MemberResourceRESTServiceSecond.java` — duplicate REST resource
- **WebLogic descriptor**: `weblogic.xml` — additionally enables `show-archived-real-path`

### ear module
- **Role**: EAR assembler
- **Packs**: `kitchensink-ear-ejb.jar` + `kitchensink-ear-web.war` + `kitchensink-ear-web2.war`
- **EAR lib dir**: `lib/`
- **Deployment descriptors**:
  - `META-INF/application.xml` — standard J2EE 1.3 EAR descriptor
  - `META-INF/weblogic-application.xml` — WebLogic-specific EAR descriptor

---

## WebLogic-Specific Artifacts

| File | Purpose |
|---|---|
| `ear/src/main/application/META-INF/weblogic-application.xml` | EAR-level: references shared library `CommonLibsWarForEar` (v1.0), configures session persistence (memory), enables cross-WAR session sharing, overrides classloading for SLF4J and Log4j |
| `web/src/main/webapp/WEB-INF/weblogic.xml` | Web-level: UTF-8 charset, JSP debug/keepgenerated, context root |
| `web2/src/main/webapp/WEB-INF/weblogic.xml` | Web-level: UTF-8 charset, JSP debug/keepgenerated, context root, show-archived-real-path |
| `ear/pom.xml` (auto-deploy profile) | `weblogic-maven-plugin` deploys to `t3://localhost:7003` |
| `CommonLibsEar.zip` | Shared library bundle referenced by weblogic-application.xml |

---

## Key Dependencies (compile/provided scope)

| Library | Version | Notes |
|---|---|---|
| `javax.enterprise:cdi-api` | via BOM | CDI 1.2 |
| `org.hibernate:hibernate-validator` | via BOM | Bean Validation |
| `org.hibernate.javax.persistence:hibernate-jpa-2.1-api` | via BOM | JPA 2.1 |
| `org.jboss.spec.javax.ejb:jboss-ejb-api_3.2_spec` | via BOM | EJB 3.2 |
| `org.slf4j:slf4j-api` | 1.7.21 | Logging facade |
| `org.slf4j:slf4j-log4j12` | 1.7.21 | Log4j bridge |
| `log4j:log4j` | 1.2.17 | Logger implementation |
| `org.apache.deltaspike.core:deltaspike-core-api/impl` | 1.8.2 | DeltaSpike CDI extensions |
| `com.google.code.gson:gson` | 2.8.6 | JSON serialization (ear scope) |
| `commons-io:commons-io` | 2.5 | IO utilities |
| `org.apache.commons:commons-lang3` | 3.5 | Lang utilities |
| `org.apache.httpcomponents:httpclient` | 4.5.3 | HTTP client |
| `junit:junit` | 4.12 | Test framework |
| `org.jboss.arquillian.*` | via BOM | Integration testing |

---

## Migration Highlights for Analysts

- **javax.** → **jakarta.** namespace migration required (all JPA, CDI, JAX-RS, Bean Validation, EJB annotations)
- **EAR structure** must be flattened to a single Spring Boot fat-jar (or multi-module Maven → multi Spring Boot modules)
- **WebLogic-specific descriptors** (`weblogic.xml`, `weblogic-application.xml`) have no Spring Boot equivalents — all config must be moved to `application.properties` or Spring beans
- **Shared library `CommonLibsWarForEar`** (from `CommonLibsEar.zip`) must be inspected and bundled explicitly as Maven dependencies
- **JNDI datasource `jdbc/SSA`** must be replaced with Spring Boot `DataSource` configuration
- **EJB `@Stateless` + JTA** → Spring `@Service` + `@Transactional`
- **CDI `@Inject`** → Spring `@Autowired` or constructor injection
- **JAX-RS** → Spring MVC `@RestController` (or keep JAX-RS via `spring-boot-starter-jersey`)
- **JSF** → Thymeleaf or another Spring Boot-compatible view technology
- **Session sharing across WARs** (weblogic-application.xml) needs architectural review
- **DeltaSpike** (CDI extension) has no direct Spring Boot equivalent; logic must be refactored
- **Log4j 1.x** → SLF4J + Logback (Spring Boot default)
- **Arquillian tests** → standard Spring Boot `@SpringBootTest` integration tests
- **Java 8 → Java 21** with `javax.*` → `jakarta.*` across all source files
