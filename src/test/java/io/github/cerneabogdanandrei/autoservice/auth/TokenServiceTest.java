package io.github.cerneabogdanandrei.autoservice.auth;

import io.github.cerneabogdanandrei.autoservice.common.security.AuthenticatedUser;
import io.github.cerneabogdanandrei.autoservice.common.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenServiceTest {

    private static final Instant NOW = Instant.parse("2030-01-15T10:00:00Z");
    private static final Duration TTL = Duration.ofMinutes(30);
    private static final String SECRET = "test-secret-that-is-at-least-32-bytes-long";

    private final JwtConfig config = new JwtConfig();
    private final JwtProperties properties = new JwtProperties(SECRET, "auto-service-api", TTL);
    private final TokenService tokenService =
            new TokenService(config.jwtEncoder(properties), properties, clockAt(NOW));

    @Test
    void issuedTokenCarriesIdentityRoleAndExpiry() {
        TokenService.IssuedToken token = tokenService.issue(7L, "employee", Role.EMPLOYEE);

        Jwt jwt = decoderAt(NOW.plusSeconds(5)).decode(token.value());

        assertThat(token.expiresInSeconds()).isEqualTo(TTL.toSeconds());
        assertThat(jwt.getSubject()).isEqualTo("employee");
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("auto-service-api");
        assertThat(jwt.getIssuedAt()).isEqualTo(NOW);
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(TTL));
        assertThat(AuthenticatedUser.from(jwt)).isEqualTo(new AuthenticatedUser(7L, "employee", Role.EMPLOYEE));
    }

    @Test
    void roleClaimBecomesSpringSecurityAuthority() {
        Jwt jwt = decoderAt(NOW).decode(tokenService.issue(1L, "owner", Role.OWNER).value());

        assertThat(config.jwtAuthenticationConverter().convert(jwt).getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_OWNER");
    }

    @Test
    void expiredTokenIsRejected() {
        String token = tokenService.issue(1L, "admin", Role.ADMIN).value();
        JwtDecoder decoderAfterExpiry = decoderAt(NOW.plus(TTL).plus(Duration.ofMinutes(2)));

        assertThatThrownBy(() -> decoderAfterExpiry.decode(token))
                .isInstanceOf(JwtException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void tokenFromAnotherIssuerIsRejected() {
        JwtProperties otherIssuer = new JwtProperties(SECRET, "someone-else", TTL);
        String token = new TokenService(config.jwtEncoder(otherIssuer), otherIssuer, clockAt(NOW))
                .issue(1L, "admin", Role.ADMIN).value();

        assertThatThrownBy(() -> decoderAt(NOW).decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtProperties otherSecret = new JwtProperties("another-secret-that-is-also-32-bytes-long!", "auto-service-api", TTL);
        String token = new TokenService(config.jwtEncoder(otherSecret), otherSecret, clockAt(NOW))
                .issue(1L, "admin", Role.ADMIN).value();

        assertThatThrownBy(() -> decoderAt(NOW).decode(token)).isInstanceOf(JwtException.class);
    }

    private JwtDecoder decoderAt(Instant instant) {
        return config.jwtDecoder(properties, clockAt(instant));
    }

    private static Clock clockAt(Instant instant) {
        return Clock.fixed(instant, ZoneOffset.UTC);
    }
}
