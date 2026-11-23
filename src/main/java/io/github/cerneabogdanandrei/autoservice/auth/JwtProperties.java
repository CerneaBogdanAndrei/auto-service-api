package io.github.cerneabogdanandrei.autoservice.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * JWT settings. The signing secret has no default anywhere: it must come from the
 * {@code JWT_SECRET} environment variable, and the application refuses to start when it is
 * missing or shorter than 256 bits (the minimum key size for HS256).
 */
@Validated
@ConfigurationProperties("app.security.jwt")
public record JwtProperties(
        @NotBlank(message = "must be set through the JWT_SECRET environment variable") String secret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl) {

    static final int MIN_SECRET_BYTES = 32;

    @AssertTrue(message = "must be at least 32 bytes (256 bits) long")
    public boolean isSecretLongEnough() {
        return secret == null || secret.isBlank() || secretBytes().length >= MIN_SECRET_BYTES;
    }

    public byte[] secretBytes() {
        return secret.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "JwtProperties[secret=****, issuer=%s, accessTokenTtl=%s]".formatted(issuer, accessTokenTtl);
    }
}
