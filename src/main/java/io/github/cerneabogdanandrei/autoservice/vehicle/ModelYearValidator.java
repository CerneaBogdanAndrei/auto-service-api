package io.github.cerneabogdanandrei.autoservice.vehicle;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Year;

/**
 * Uses the application {@link Clock} when created by Spring (so "next year" is testable);
 * falls back to the system clock when Bean Validation is bootstrapped without Spring.
 */
public class ModelYearValidator implements ConstraintValidator<ModelYear, Integer> {

    static final int FIRST_MODEL_YEAR = 1886;

    private final Clock clock;

    public ModelYearValidator() {
        this(Clock.systemUTC());
    }

    @Autowired
    public ModelYearValidator(Clock clock) {
        this.clock = clock;
    }

    @Override
    public boolean isValid(Integer year, ConstraintValidatorContext context) {
        return year == null || (year >= FIRST_MODEL_YEAR && year <= Year.now(clock).getValue() + 1);
    }
}
