# Legacy Repository Overview

**Project:** kitchensink-ear (`org.wildfly.quickstarts:kitchensink-ear:11.0.0-SNAPSHOT`)

## Build System
- Maven 3, multi-module POM at `original_app/pom.xml`
- Java EE 7 / WildFly 11 (originally packaged as EAR)

## Modules
| Module | Artifact ID | Packaging | Purpose |
|---|---|---|---|
| `ejb` | kitchensink-ear-ejb | ejb | Business logic, JPA entities, CDI beans |
| `web` | kitchensink-ear-web | war | Primary JSF web frontend |
| `web2` | kitchensink-ear-web2 | war | Secondary web module |
| `ear` | kitchensink-ear | ear | EAR assembler (packages ejb + war) |

## Key Technologies (legacy)
- **Runtime:** WildFly 11 / JBoss EAP 7 (javax.* APIs)
- **Persistence:** JPA 2.1 (javax.persistence), H2 in-memory DB
- **Web:** JSF 2.2, JAX-RS, CDI, Bean Validation
- **EJB:** Stateless session beans, CMT
- **Build plugins:** wildfly-maven-plugin, wro4j

## Entry Points
- EAR packaging: `original_app/ear/` assembles the deployable
- JSF managed beans / REST resources in `original_app/web/`

## Migration Target
- Java 21 / Spring Boot 3.4 / Maven 3.9 / Jakarta EE 10 namespace
- All downstream analysis to be done by the 4 specialist analysts.
