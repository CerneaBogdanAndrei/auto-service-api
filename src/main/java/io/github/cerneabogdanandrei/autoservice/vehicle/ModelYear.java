package io.github.cerneabogdanandrei.autoservice.vehicle;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A plausible model year: from 1886 (the first production automobile) up to next year,
 * since manufacturers release next year's models in advance.
 */
@Documented
@Constraint(validatedBy = ModelYearValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ModelYear {

    String message() default "must be between 1886 and next year";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
