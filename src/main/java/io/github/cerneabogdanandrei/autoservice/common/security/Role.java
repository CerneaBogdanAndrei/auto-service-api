package io.github.cerneabogdanandrei.autoservice.common.security;

/**
 * Application roles. Exposed to Spring Security as {@code ROLE_<name>} authorities.
 */
public enum Role {
    /** Full access, including user management. */
    ADMIN,
    /** Everything except user management, plus reports. */
    OWNER,
    /** Reads clients, vehicles and parts; works only on the interventions assigned to them. */
    EMPLOYEE
}
