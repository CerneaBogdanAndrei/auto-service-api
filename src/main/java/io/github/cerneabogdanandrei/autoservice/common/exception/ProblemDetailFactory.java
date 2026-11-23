package io.github.cerneabogdanandrei.autoservice.common.exception;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Builds RFC 9457 Problem Details with the API's extension properties:
 * {@code code} (machine-readable error code), {@code timestamp} and, for validation
 * failures, {@code errors}. Shared by the MVC exception handler and the security
 * entry points so every error response has the same shape.
 */
@Component
public class ProblemDetailFactory {

    static final String CODE = "code";
    static final String TIMESTAMP = "timestamp";
    static final String ERRORS = "errors";

    private final Clock clock;

    public ProblemDetailFactory(Clock clock) {
        this.clock = clock;
    }

    public ProblemDetail create(ErrorCode code, String detail, @Nullable String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setProperty(CODE, code.name());
        complete(problem, path);
        return problem;
    }

    public ProblemDetail validation(List<ValidationError> errors, @Nullable String path) {
        ProblemDetail problem = create(ErrorCode.VALIDATION_FAILED, "Request validation failed.", path);
        problem.setProperty(ERRORS, errors);
        return problem;
    }

    /**
     * Adds the API extension properties to a Problem Detail created by the framework.
     */
    public void enrich(ProblemDetail problem, @Nullable String path) {
        Map<String, Object> properties = problem.getProperties();
        if (properties == null || !properties.containsKey(CODE)) {
            problem.setProperty(CODE, ErrorCode.fromStatus(HttpStatusCode.valueOf(problem.getStatus())).name());
        }
        complete(problem, path);
    }

    private void complete(ProblemDetail problem, @Nullable String path) {
        if (problem.getInstance() == null && path != null) {
            problem.setInstance(URI.create(path));
        }
        Map<String, Object> properties = problem.getProperties();
        if (properties == null || !properties.containsKey(TIMESTAMP)) {
            problem.setProperty(TIMESTAMP, Instant.now(clock));
        }
    }
}
