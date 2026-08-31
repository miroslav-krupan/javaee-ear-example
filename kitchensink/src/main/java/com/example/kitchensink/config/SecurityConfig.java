package com.example.kitchensink.config;

import org.springframework.context.annotation.Configuration;

/**
 * Security placeholder — no application-level security exists in the source EAR
 * (no web.xml security-constraint, no @RolesAllowed, no WebLogic realm configuration).
 * Access control was enforced at the WebLogic server layer, outside the application boundary.
 *
 * Decision (target-architecture §8): Spring Security is intentionally excluded from this
 * migration pass. Scope is functional equivalence only. Add spring-boot-starter-security
 * and define access-control rules in a follow-up sprint once requirements are formally
 * specified. All requests are permitted by default (no security filter chain active).
 */
@Configuration
public class SecurityConfig {
    // Intentionally empty — see Javadoc above.
}
