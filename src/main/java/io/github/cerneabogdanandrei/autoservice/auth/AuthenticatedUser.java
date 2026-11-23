package io.github.cerneabogdanandrei.autoservice.auth;

import io.github.cerneabogdanandrei.autoservice.user.Role;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Objects;

/**
 * The caller of the current request, built from the verified access token.
 * Controllers declare it as a method parameter and pass it to services, which keeps
 * the service layer free of static security-context lookups and easy to unit test.
 */
public record AuthenticatedUser(Long id, String username, Role role) {

    public AuthenticatedUser {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(role, "role");
    }

    public static AuthenticatedUser from(Jwt jwt) {
        Object userId = jwt.getClaim(TokenService.USER_ID_CLAIM);
        if (!(userId instanceof Number number)) {
            throw new IllegalArgumentException("Access token has no numeric '%s' claim".formatted(TokenService.USER_ID_CLAIM));
        }
        return new AuthenticatedUser(number.longValue(), jwt.getSubject(),
                Role.valueOf(jwt.getClaimAsString(TokenService.ROLE_CLAIM)));
    }

    public boolean hasRole(Role expected) {
        return role == expected;
    }
}
