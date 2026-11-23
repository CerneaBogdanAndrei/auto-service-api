package io.github.cerneabogdanandrei.autoservice.common.exception;

import java.util.Map;
import java.util.Objects;

/**
 * Thrown by the service layer when an operation would break a business rule.
 * The HTTP status comes from the {@link ErrorCode} (409 for business conflicts);
 * optional properties are added to the Problem Details body (e.g. available stock).
 */
public class BusinessRuleException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> properties;

    public BusinessRuleException(ErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public BusinessRuleException(ErrorCode code, String message, Map<String, Object> properties) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.properties = Map.copyOf(properties);
    }

    public ErrorCode getCode() {
        return code;
    }

    public Map<String, Object> getProperties() {
        return properties;
    }
}
