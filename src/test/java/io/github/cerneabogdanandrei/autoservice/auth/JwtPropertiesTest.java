package io.github.cerneabogdanandrei.autoservice.auth;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JwtPropertiesTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @Test
    void acceptsSecretOfAtLeast32Bytes() {
        assertThat(validator.validate(properties("x".repeat(32)))).isEmpty();
    }

    @Test
    void rejectsMissingSecret() {
        Set<ConstraintViolation<JwtProperties>> violations = validator.validate(properties(""));

        assertThat(violations).singleElement()
                .satisfies(violation -> {
                    assertThat(violation.getPropertyPath()).hasToString("secret");
                    assertThat(violation.getMessage()).contains("JWT_SECRET");
                });
    }

    @Test
    void rejectsSecretShorterThan256Bits() {
        Set<ConstraintViolation<JwtProperties>> violations = validator.validate(properties("x".repeat(31)));

        assertThat(violations).singleElement()
                .satisfies(violation -> assertThat(violation.getMessage()).contains("32 bytes"));
    }

    @Test
    void toStringNeverRevealsTheSecret() {
        assertThat(properties("super-secret-value-0123456789abcdef")).asString()
                .doesNotContain("super-secret")
                .contains("****");
    }

    private static JwtProperties properties(String secret) {
        return new JwtProperties(secret, "auto-service-api", Duration.ofHours(1));
    }
}
