package io.github.cerneabogdanandrei.autoservice.user;

import io.github.cerneabogdanandrei.autoservice.common.security.Role;
import org.springframework.data.jpa.domain.Specification;

/**
 * Optional list filters; a {@code null} argument means "no restriction".
 */
final class UserSpecifications {

    private UserSpecifications() {
    }

    static Specification<User> hasRole(Role role) {
        return (root, query, cb) -> role == null ? null : cb.equal(root.get("role"), role);
    }

    static Specification<User> isEnabled(Boolean enabled) {
        return (root, query, cb) -> enabled == null ? null : cb.equal(root.get("enabled"), enabled);
    }
}
