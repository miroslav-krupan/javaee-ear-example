# Repository Overview — kitchensink-ear

## Identity
- **Artifact**: `org.wildfly.quickstarts:kitchensink-ear` v11.0.0-SNAPSHOT
- **App server**: WildFly / JBoss EAP (Java EE 7)
- **Build**: Maven multi-module, Java 8 source/target
- **Final packaging**: EAR

## Module structure

| Module | Artifact ID | Packaging | Role |
|--------|-------------|-----------|------|
| `ejb`  | `kitchensink-ear-ejb` | ejb-jar | Domain model, persistence, service layer |
| `web`  | `kitchensink-ear-web` | WAR | Primary UI (JSF) + REST API |
| `web2` | `kitchensink-ear-web2` | WAR | Secondary WAR — mirrors web module |
| `ear`  | `kitchensink-ear` | EAR | Assembly: packages ejb + web + web2 |

## Technologies in use
- **CDI** — dependency injection (`beans.xml` in ejb + both WARs)
- **EJB** — `@Stateless` `MemberRegistration` service
- **JPA / Hibernate** — `Member` entity; persistence unit `primary`; JNDI datasource `jdbc/SSA`; `hbm2ddl.auto=create-drop`
- **JAX-RS** — `MemberResourceRESTService` / `MemberResourceRESTServiceSecond`; activated via `JaxRsActivator` (extends `Application`)
- **JSF** — `MemberController` / `MemberControllerSecond` backing beans; views under `web/src/main/webapp`
- **Bean Validation** — applied to `Member` entity fields
- **Arquillian** — integration test `MemberRegistrationIT` (container-managed)

## Key classes (ejb module)
| Class | Role |
|-------|------|
| `model/Member` | JPA entity — id, name, email, phoneNumber |
| `data/MemberRepository` | `@ApplicationScoped` JPQL queries over `EntityManager` |
| `data/MemberListProducer` | CDI producer — `@Produces @Named` ordered member list |
| `service/MemberRegistration` | `@Stateless` EJB — persist + fire CDI event |
| `util/Resources` | CDI producer for `EntityManager`, `Logger` |

## Entry points
- **REST**: `GET/POST /members` exposed by both `MemberResourceRESTService` (web) and `MemberResourceRESTServiceSecond` (web2)
- **UI**: JSF Facelets in `web/src/main/webapp/` and `web2/src/main/webapp/`

## Data source
- JNDI name: `jdbc/SSA`
- Defined in: `ear/src/main/application/META-INF/kitchensink-ear-quickstart-ds.xml`
- H2 in-memory used for dev/test (`test-persistence.xml`)

## Migration target
Java 21 / Spring Boot 3.4 / Maven 3.9 / Jakarta EE namespace
