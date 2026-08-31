# Repository Overview — kitchensink-ear

## Repository

- **URL:** https://github.com/miroslav-krupan/javaee-ear-example
- **Default branch:** master
- **Version:** 11.0.0-SNAPSHOT

## Build Tool & Java Version

- **Build:** Maven 3.x (multi-module POM)
- **Java source/target:** 8 (all modules)
- **Java EE spec BOM:** `wildfly-javaee7-with-tools:11.0.0.CR1` → **Java EE 7**
- **JPA spec:** `hibernate-jpa-2.1-api` (JPA 2.1)

## Application Server

- **Target runtime:** Oracle WebLogic (12.2.1-3-0)
- **Deployment descriptors:** `weblogic-application.xml` (EAR), `weblogic.xml` (each WAR)
- **Session store:** in-memory, cross-module sharing enabled (`sharing-enabled=true`)
- **Shared library:** `CommonLibsWarForEar` (referenced via `weblogic-application.xml`; bundled as `CommonLibsEar.zip`)
- **Class-loading overrides:** `org.slf4j.*`, `log4j.*` preferred from application

## Packaging

Top-level artifact is an **EAR** (`kitchensink-ear-ear`) containing:

| Module | Artifact | Context root |
|--------|----------|-------------|
| EJB JAR | `kitchensink-ear-ejb.jar` | — |
| WAR 1 | `kitchensink-ear-web.war` | `/kitchensink-ear-web` |
| WAR 2 | `kitchensink-ear-web2.war` | `/kitchensink-ear-web2` |

EAR `lib/` directory bundles shared JARs (DeltaSpike, Gson, SLF4J/Log4j).

## Module Structure

```
kitchensink-ear/          ← parent POM (pom packaging)
  ejb/                    ← EJB 3.0 module (business logic + JPA entities + CDI)
  web/                    ← WAR 1 (JSF + JAX-RS + CDI controller)
  web2/                   ← WAR 2 (JSF + JAX-RS + CDI controller, second context)
  ear/                    ← EAR assembler module
```

### ejb module

- **Packaging:** EJB 3.0 JAR
- **Key classes:**
  - `model/Member.java` — JPA entity (Bean Validation annotations)
  - `data/MemberRepository.java` — CDI bean, JPA queries
  - `data/MemberListProducer.java` — CDI producer for member list
  - `service/MemberRegistration.java` — Stateless EJB, JTA transactions
  - `util/Resources.java` — CDI producers (EntityManager, Logger)
- **Persistence:** JPA 2.1, persistence-unit `primary`, JNDI datasource `jdbc/SSA`, Hibernate DDL `create-drop`
- **Test:** Arquillian integration test (`MemberRegistrationIT`)

### web module

- **Packaging:** WAR
- **Key classes:**
  - `controller/MemberController.java` — JSF backing bean (CDI)
  - `rest/MemberResourceRESTService.java` — JAX-RS resource
  - `rest/JaxRsActivator.java` — JAX-RS application activator
  - `util/WebResources.java` — CDI producers (FacesContext, HttpSession)
- **Descriptors:** `faces-config.xml`, `web.xml`, `weblogic.xml`, `beans.xml`
- **UI:** JSF 2.2 (`.xhtml` templates expected under `webapp/`)
- **Logging:** Log4j via `WEB-INF/classes/log4j.xml`

### web2 module

- **Packaging:** WAR (mirrors `web` with suffix `Second` on class names)
- **Key classes:** `MemberControllerSecond`, `MemberResourceRESTServiceSecond`, `JaxRsActivator`
- Same descriptor set as `web`; separate context root `/kitchensink-ear-web2`

### ear module

- **Packaging:** EAR
- **WebLogic deploy plugin** (`com.oracle.weblogic:weblogic-maven-plugin`) in `auto-deploy` profile
- Bundles: EJB JAR + both WARs + shared libs in `lib/`

## Key Java EE APIs in Use

| API | Usage |
|-----|-------|
| EJB 3.2 | Stateless session bean (`MemberRegistration`) |
| JPA 2.1 | Entity (`Member`), repositories, JTA datasource |
| CDI 1.x | Injection, producers, events throughout |
| JSF 2.2 | UI layer (both WARs) |
| JAX-RS 2.0 | REST endpoints (both WARs) |
| Bean Validation 1.1 | Entity constraints |
| JTA | Transaction management via container |

## Third-Party Dependencies (notable)

| Dependency | Version | Notes |
|-----------|---------|-------|
| DeltaSpike Core | 1.8.2 | CDI extension (scope bridging) |
| Gson | 2.8.6 | JSON serialisation |
| SLF4J + Log4j | 1.7.21 / 1.2.17 | Logging (provided; app-preferred) |
| Hibernate Validator | container | Bean Validation impl |
| JUnit 4 | 4.12 | Unit/integration tests |
| Arquillian | container | In-container integration tests |

## Migration Target

**Java 21 / Spring Boot 3.4 / Maven 3.9 / Jakarta EE** (namespace `jakarta.*`)
