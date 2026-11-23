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
    private static final Map<String, ConstraintError> KNOWN_CONSTRAINTS = Map.of();

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
