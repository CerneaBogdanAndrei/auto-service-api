package io.github.cerneabogdanandrei.autoservice.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Machine-readable error codes returned in the {@code code} property of every
 * Problem Details response. Each code is bound to the HTTP status it is returned with.
 */
public enum ErrorCode {

    // 400 — the request itself is invalid
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST),

    // 401 / 403 — authentication and authorization
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),

    // 404 / 405 / 406 / 415 — protocol-level errors
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    // 409 — the request conflicts with the current state of the system
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT),
    DATA_INTEGRITY_VIOLATION(HttpStatus.CONFLICT),
    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT),
    OWN_ACCOUNT_PROTECTED(HttpStatus.CONFLICT),
    USER_HAS_INTERVENTIONS(HttpStatus.CONFLICT),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT),
    CLIENT_HAS_APPOINTMENTS(HttpStatus.CONFLICT),
    VIN_ALREADY_EXISTS(HttpStatus.CONFLICT),
    VEHICLE_HAS_APPOINTMENTS(HttpStatus.CONFLICT),
    PART_CODE_ALREADY_EXISTS(HttpStatus.CONFLICT),
    PART_IN_USE(HttpStatus.CONFLICT),
    APPOINTMENT_IN_PAST(HttpStatus.CONFLICT),
    APPOINTMENT_OVERLAP(HttpStatus.CONFLICT),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT),
    APPOINTMENT_NOT_MODIFIABLE(HttpStatus.CONFLICT),
    APPOINTMENT_NOT_IN_PROGRESS(HttpStatus.CONFLICT),
    INVALID_ASSIGNEE(HttpStatus.CONFLICT),
    INSUFFICIENT_STOCK(HttpStatus.CONFLICT),

    // Fallbacks
    REQUEST_REJECTED(HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    /** Shared by the security filter chain and method security so both 403s read the same. */
    public static final String ACCESS_DENIED_DETAIL = "You do not have permission to perform this action.";

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }

    /**
     * Code used for errors raised by the framework itself (e.g. an unsupported HTTP method),
     * where only the status is known.
     */
    public static ErrorCode fromStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> MALFORMED_REQUEST;
            case 401 -> UNAUTHORIZED;
            case 403 -> ACCESS_DENIED;
            case 404 -> RESOURCE_NOT_FOUND;
            case 405 -> METHOD_NOT_ALLOWED;
            case 406 -> NOT_ACCEPTABLE;
            case 409 -> DATA_INTEGRITY_VIOLATION;
            case 415 -> UNSUPPORTED_MEDIA_TYPE;
            default -> status.is5xxServerError() ? INTERNAL_ERROR : REQUEST_REJECTED;
        };
    }
}
