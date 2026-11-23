package io.github.cerneabogdanandrei.autoservice.vehicle;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Vehicle Identification Number as defined by ISO 3779: exactly 17 characters, digits and
 * Latin letters except I, O and Q (which are easily confused with 1 and 0). Case-insensitive
 * here; the service stores VINs in upper case.
 */
@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@ReportAsSingleViolation
@Pattern(regexp = "^[A-HJ-NPR-Za-hj-npr-z0-9]{17}$")
public @interface Vin {

    String message() default "must be a 17-character VIN (digits and letters except I, O and Q)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
