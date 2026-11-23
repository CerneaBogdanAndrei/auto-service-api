package io.github.cerneabogdanandrei.autoservice.common.exception;

import org.springframework.lang.Nullable;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates database constraint violations into API errors.
 *
 * <p>The service layer checks business rules before writing, so these mappings only kick in
 * when two concurrent requests race past the same check; the database constraint then
 * rejects one of them and the client still receives the specific 409 error.
 */
final class DatabaseConstraints {

    /** PostgreSQL reports the violated constraint as: ... constraint "name" ... */
    private static final Pattern CONSTRAINT_NAME = Pattern.compile("constraint \"([^\"]+)\"");

    /** Known constraint names and the error they represent. Extended as features are added. */
    private static final Map<String, ConstraintError> KNOWN_CONSTRAINTS = Map.ofEntries(
            Map.entry("uq_users_username", new ConstraintError(ErrorCode.USERNAME_ALREADY_EXISTS,
                    "A user with this username already exists.")),
            Map.entry("fk_interventions_assigned_user", new ConstraintError(ErrorCode.USER_HAS_INTERVENTIONS,
                    "The user has assigned interventions and cannot be deleted; disable the account instead.")),
            Map.entry("uq_clients_email", new ConstraintError(ErrorCode.EMAIL_ALREADY_EXISTS,
                    "A client with this email already exists.")),
            Map.entry("uq_vehicles_vin", new ConstraintError(ErrorCode.VIN_ALREADY_EXISTS,
                    "A vehicle with this VIN already exists.")),
            Map.entry("fk_appointments_vehicle", new ConstraintError(ErrorCode.VEHICLE_HAS_APPOINTMENTS,
                    "The vehicle has appointments and cannot be deleted.")),
            Map.entry("uq_parts_code", new ConstraintError(ErrorCode.PART_CODE_ALREADY_EXISTS,
                    "A part with this code already exists.")),
            Map.entry("fk_intervention_parts_part", new ConstraintError(ErrorCode.PART_IN_USE,
                    "The part is used by interventions and cannot be deleted.")),
            Map.entry("ex_appointments_vehicle_overlap", new ConstraintError(ErrorCode.APPOINTMENT_OVERLAP,
                    "The vehicle already has an appointment in this interval."))
    );

    private DatabaseConstraints() {
    }

    record ConstraintError(ErrorCode code, String detail) {
    }

    static Optional<ConstraintError> errorFor(@Nullable String constraintName) {
        return constraintName == null ? Optional.empty() : Optional.ofNullable(KNOWN_CONSTRAINTS.get(constraintName));
    }

    /**
     * Finds the name of the violated constraint in the exception chain, preferring the name
     * extracted by Hibernate and falling back to the database error message.
     */
    static Optional<String> constraintName(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = nextCause(cause)) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation
                    && violation.getConstraintName() != null) {
                return Optional.of(violation.getConstraintName());
            }
        }
        for (Throwable cause = exception; cause != null; cause = nextCause(cause)) {
            if (cause.getMessage() != null) {
                Matcher matcher = CONSTRAINT_NAME.matcher(cause.getMessage());
                if (matcher.find()) {
                    return Optional.of(matcher.group(1));
                }
            }
        }
        return Optional.empty();
    }

    @Nullable
    private static Throwable nextCause(Throwable throwable) {
        Throwable cause = throwable.getCause();
        return cause == throwable ? null : cause;
    }
}
