# Repository Overview — javaee-ear-example (kitchensink-ear)

## Project Identity
- **Artifact:** `org.wildfly.quickstarts:kitchensink-ear:11.0.0-SNAPSHOT`
- **Description:** WildFly Quickstart demonstrating JSF, CDI, JAX-RS, EJB, JPA, and Bean Validation packaged as an EAR.
- **Build system:** Maven 3 (multi-module POM), Java source/target 8.
- **Java EE Platform:** Java EE 7 (via `jboss-javaee-7.0` BOM + `wildfly-javaee7-with-tools` BOM).
- **Target runtime:** WildFly / JBoss EAP (uses `weblogic-application.xml` and `weblogic.xml` descriptors for WebLogic compatibility overlay).

## Module Structure

| Module | Packaging | Artifact | Context Root |
|--------|-----------|----------|--------------|
| `ejb`  | EJB JAR   | `kitchensink-ear-ejb.jar` | — (shared backend) |
| `web`  | WAR       | `kitchensink-ear-web.war` | `/kitchensink-ear-web` |
| `web2` | WAR       | `kitchensink-ear-web2.war` | `/kitchensink-ear-web2` |
| `ear`  | EAR       | `kitchensink-ear.ear` | (top-level assembly) |

## Key Components

### EJB Module (`ejb/`)
- **Domain model:** `Member` entity — JPA `@Entity` mapped to `AA_Registrant` table; fields: `id`, `name`, `email`, `phoneNumber`. Uses `javax.persistence.*` and Hibernate Validator constraints (`@Email`, `@NotEmpty` from legacy `org.hibernate.validator.constraints`).
- **Data access:** `MemberRepository` (CDI/JPA), `MemberListProducer` (CDI producer for `List<Member>`).
- **Service:** `MemberRegistration` — `@Stateless` EJB for member registration.
- **CDI utilities:** `Resources` — produces `EntityManager`, `Logger`.
- **Tests:** `MemberRegistrationIT` — Arquillian integration test.

### Web Module (`web/`)
- **JSF Controller:** `MemberController` — CDI `@Model` (request-scoped controller).
- **JAX-RS:** `JaxRsActivator` (activates REST), `MemberResourceRESTService` — REST endpoint.
- **CDI utility:** `WebResources` — produces `FacesContext`.
- **Descriptors:** `WEB-INF/web.xml`, `WEB-INF/weblogic.xml` (WebLogic WAR config).

### Web2 Module (`web2/`)
- Mirror of `web/` with `*Second` variants: `MemberControllerSecond`, `MemberResourceRESTServiceSecond`, plus its own `JaxRsActivator` and `WebResources`.
- Descriptors: `WEB-INF/web.xml`, `WEB-INF/weblogic.xml`.

### EAR Module (`ear/`)
- `META-INF/application.xml` — declares EJB jar + two WARs.
- `META-INF/weblogic-application.xml` — WebLogic-specific: shared library ref (`CommonLibsWarForEar`), in-memory HTTP session store, session sharing enabled, class-loading preference for `org.slf4j.*` and `log4j.*`.
- `CommonLibsEar.zip` at repo root — external shared library bundle referenced by `weblogic-application.xml`.

## Entry Points
- HTTP: two WARs under separate context roots; JAX-RS activated via `JaxRsActivator` in each.
- No main class; application server deploys the EAR.

## Migration Complexity Signals
- **WebLogic-specific:** `weblogic-application.xml` (shared library ref, session descriptor), `weblogic.xml` in both WARs → requires WebLogic binding analysis.
- **Legacy javax.* namespace:** all imports use `javax.*` → must migrate to `jakarta.*` for Jakarta EE 10 / Spring Boot 3.
- **Deprecated Hibernate Validator annotations:** `org.hibernate.validator.constraints.Email` / `NotEmpty` → replaced by `jakarta.validation.constraints.*`.
- **EAR packaging → decompose:** Spring Boot favors flat JAR; EAR modularization must be redesigned.
- **Dual-WAR pattern:** two near-identical web modules; consolidation opportunity.
- **Arquillian tests:** will need replacement with Spring Boot test framework.
- **Spring Framework 4.3 dependency declared** in root POM (not currently used in module code found) — presence needs clarification.
