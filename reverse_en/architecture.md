# As-Is Architecture — kitchensink-ear

## 1. Packaging & Deployment

The application is deployed to **Oracle WebLogic 12.2.1-3-0** as a single **EAR** (`kitchensink-ear-ear-11.0.0-SNAPSHOT.ear`).

```
kitchensink-ear-ear.ear
├── META-INF/
│   ├── application.xml          ← J2EE 1.3 EAR descriptor; declares modules + context roots
│   └── weblogic-application.xml ← WebLogic EAR extensions (shared lib, session store, class-loading)
├── kitchensink-ear-ejb.jar      ← EJB 3.0 module (business logic, entities, data access)
├── kitchensink-ear-web.war      ← WAR 1 — context root /kitchensink-ear-web
├── kitchensink-ear-web2.war     ← WAR 2 — context root /kitchensink-ear-web2
└── lib/
    ├── deltaspike-core-*.jar    ← CDI extension (config, scope bridging)
    ├── gson-2.8.6.jar
    └── slf4j / log4j jars
```

### WebLogic EAR-level settings (`weblogic-application.xml`)
- **Shared library reference:** `CommonLibsWarForEar 1.0` (pre-deployed library, `exact-match=false`).
- **Session persistence:** in-memory (`persistent-store-type=memory`), cross-module sharing enabled (`sharing-enabled=true`).
- **Class-loading overrides:** `org.slf4j.*` and `log4j.*` are preferred from the application rather than the server.

### WAR-level settings (`weblogic.xml` — identical in both WARs)
- UTF-8 character set for all request paths (`/*`).
- JSP kept-generated + debug mode.
- Context root also declared here (redundant with `application.xml`).

---

## 2. Module / Layer Structure

```
┌─────────────────────────────────────────────────────┐
│                   WebLogic EAR                      │
│                                                     │
│  ┌──────────────────┐   ┌──────────────────────┐   │
│  │   web WAR        │   │   web2 WAR           │   │
│  │  /kitchensink-   │   │  /kitchensink-       │   │
│  │   ear-web        │   │   ear-web2           │   │
│  │                  │   │                      │   │
│  │ JSF controller   │   │ JSF controller       │   │
│  │ JAX-RS resource  │   │ JAX-RS resource      │   │
│  │ CDI util beans   │   │ CDI util beans       │   │
│  └────────┬─────────┘   └──────────┬───────────┘   │
│           │  CDI injection (EJB    │               │
│           └──────────┬────────────┘               │
│                      ▼                             │
│  ┌───────────────────────────────────────────────┐ │
│  │              ejb JAR                          │ │
│  │  MemberRegistration (Stateless EJB / CMT)     │ │
│  │  MemberRepository   (CDI @ApplicationScoped)  │ │
│  │  MemberListProducer (CDI @RequestScoped)      │ │
│  │  Member             (JPA Entity)              │ │
│  │  Resources          (CDI producers)           │ │
│  └───────────────┬───────────────────────────────┘ │
│                  │  JTA / JNDI datasource           │
│                  ▼  jdbc/SSA                        │
│           [ Relational Database ]                   │
└─────────────────────────────────────────────────────┘
```

---

## 3. Component Catalogue

### 3.1 EJB Module (`ejb/`)

| Component | Type | Key annotations / APIs |
|-----------|------|------------------------|
| `MemberRegistration` | **Stateless Session EJB** (CMT) | `@Stateless`, injects `EntityManager`, fires CDI `Event<Member>` on `register()` |
| `MemberRepository` | CDI bean (`@ApplicationScoped`) | JPA Criteria API; `findById`, `findByEmail`, `findAllOrderedByName` |
| `MemberListProducer` | CDI bean (`@RequestScoped`) | `@Produces @Named` list of members; `@Observes` `Member` events to refresh |
| `Member` | JPA `@Entity` | Table `AA_Registrant`; `@UniqueConstraint(email)`; Bean Validation (`@NotNull`, `@Size`, `@Pattern`, `@Email`, `@Digits`) |
| `Resources` | CDI producer bean | Produces `EntityManager` (`@PersistenceContext(unitName="primary")`) and SLF4J `Logger` |

### 3.2 WAR 1 (`web/`)

| Component | Type | Key annotations |
|-----------|------|-----------------|
| `MemberController` | JSF backing bean | `@Model` (= `@RequestScoped` + EL name); calls `MemberRegistration.register()`; uses Gson for logging |
| `MemberResourceRESTService` | JAX-RS resource | `@Path("/members")` `@RequestScoped`; `GET /members`, `GET /members/{id}`, `POST /members`; validates with `javax.validation.Validator`; delegates to `MemberRegistration` + `MemberRepository` |
| `JaxRsActivator` | JAX-RS application | `@ApplicationPath("/rest")` — activates JAX-RS without `web.xml` servlet entry |
| `WebResources` | CDI producer | Produces `FacesContext` (`@RequestScoped`) |

REST base URL: `https://<host>/kitchensink-ear-web/rest/members`

### 3.3 WAR 2 (`web2/`)

Structurally mirrors WAR 1 with renamed classes (`MemberControllerSecond`, `MemberResourceRESTServiceSecond`). Notable difference: `MemberControllerSecond` also calls **DeltaSpike** `ConfigResolver.getPropertyValue("config.key", "Default value")` to illustrate external configuration via the CDI extension.

REST base URL: `https://<host>/kitchensink-ear-web2/rest/members`

Both WARs operate on the **same** backing EJB and JPA entity — they share the single `MemberRegistration` stateless EJB and `MemberRepository` CDI bean from the EJB module.

---

## 4. Persistence

| Setting | Value |
|---------|-------|
| JPA version | 2.1 (spec: `javax.persistence`) |
| Persistence unit | `primary` |
| Transaction type | JTA (container-managed) |
| Datasource JNDI | `jdbc/SSA` (defined in WebLogic server config, not in the EAR) |
| DDL strategy | `hibernate.hbm2ddl.auto=create-drop` (development only) |
| SQL logging | `hibernate.show_sql=true` |
| Entity | `Member` → table `AA_Registrant` |

The `EntityManager` is produced by `Resources.em` via `@PersistenceContext` and injected into `MemberRegistration` and `MemberRepository` through CDI.

---

## 5. Transactions

All business write operations go through `MemberRegistration.register()`, which is a **Container-Managed Transaction (CMT)** `@Stateless` EJB. The default transaction attribute is `REQUIRED`, so every call to `register()` runs inside a JTA transaction demarcated by WebLogic.

`MemberRepository` is a CDI bean (not an EJB) — it participates in the ambient transaction but does not declare one itself.

---

## 6. Java EE APIs in Use

| API | Version | Usage |
|-----|---------|-------|
| EJB | 3.2 | Stateless session bean (`MemberRegistration`) — CMT transactions |
| JPA | 2.1 | Entity persistence, JTA datasource, Criteria API queries |
| CDI | 1.x | `@Inject`, `@Produces`, `@Observes`, `@ApplicationScoped`, `@RequestScoped`, `@Named`, `@Model` |
| JSF | 2.2 | UI layer in both WARs (Facelets; `faces-config.xml`; backing beans via `@Model`) |
| JAX-RS | 2.0 | REST endpoints (`@ApplicationPath`, `@Path`, `@GET`, `@POST`) producing JSON |
| Bean Validation | 1.1 | Entity-level constraints; programmatic `Validator` in REST layer |
| JTA | 1.2 | Transaction management via container (datasource JNDI `jdbc/SSA`) |
| JAXB | 2.x | `@XmlRootElement` on `Member` for potential XML marshalling |

---

## 7. Cross-Cutting Concerns

### Logging
- SLF4J API (1.7.21) + Log4j 1.2.17 implementation, configured via `WEB-INF/classes/log4j.xml` in each WAR.
- `Resources` produces a typed SLF4J `Logger` per injection point.
- WebLogic class-loading is overridden so the application's logging jars take precedence over server-bundled ones.

### Configuration
- **DeltaSpike Core 1.8.2** is bundled in `ear/lib/`. `MemberControllerSecond` uses `ConfigResolver` — DeltaSpike's portable config abstraction that reads from system properties, environment variables, and custom `ConfigSource` implementations.
- No application-level `*.properties` config file is present in the repo; the `config.key` property falls back to the literal default `"Default value"`.

### Security
- No security constraints are declared in `web.xml` or `weblogic.xml`. No `@RolesAllowed` / `@PermitAll` EJB annotations are present. Security is either handled at the WebLogic level (not captured in the repo) or absent in this application.

### Session Management
- HTTP sessions use the WebLogic in-memory store.
- Cross-WAR session sharing is explicitly enabled (`sharing-enabled=true`), allowing a single session cookie to span both context roots.

---

## 8. Runtime Container Dependencies (WebLogic-provided)

| Capability | How the app relies on it |
|------------|--------------------------|
| JTA transaction manager | CMT on `@Stateless` EJB; `@PersistenceContext` with JTA datasource |
| JNDI datasource `jdbc/SSA` | `<jta-data-source>` in `persistence.xml` |
| EJB container | Lifecycle + transaction management of `MemberRegistration` |
| JSF runtime | Facelets rendering for both WARs |
| JAX-RS runtime | Jersey (or WebLogic built-in) wired via `@ApplicationPath` |
| CDI container (Weld) | Bean scanning, injection, event bus |
| Shared library slot | `CommonLibsWarForEar` pre-deployed on the server |
| Class-loader hierarchy | EAR-level isolation + WAR-level class-loading with overrides |

---

## 9. Third-Party Dependencies (bundled in EAR lib/)

| Library | Version | Purpose |
|---------|---------|---------|
| DeltaSpike Core | 1.8.2 | CDI-based portable config + scope bridging |
| Gson | 2.8.6 | JSON serialisation (used in `MemberController` for debug logging) |
| SLF4J API | 1.7.21 | Logging façade |
| Log4j | 1.2.17 | Logging implementation |
| Hibernate Validator | container | Bean Validation (provided by WebLogic) |

---

## 10. Test Infrastructure

- **Arquillian** in-container integration tests (`MemberRegistrationIT`) run against the EJB module.
- **JUnit 4** as the test runner.
- A separate `test-persistence.xml` with its own persistence unit is used for tests, pointing at a test datasource.
