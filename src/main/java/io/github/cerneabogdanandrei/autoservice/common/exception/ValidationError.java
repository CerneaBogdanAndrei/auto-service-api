package io.github.cerneabogdanandrei.autoservice.common.exception;

/**
 * A single validation failure, listed in the {@code errors} property of a 400 response.
 * Rejected values are deliberately not echoed back (they may contain secrets such as passwords).
 */
public record ValidationError(String field, String message) {
}
