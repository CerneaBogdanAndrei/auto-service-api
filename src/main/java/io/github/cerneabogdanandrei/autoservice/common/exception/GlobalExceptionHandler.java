package io.github.cerneabogdanandrei.autoservice.common.exception;

import com.fasterxml.jackson.databind.JsonMappingException.Reference;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.lang.Nullable;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Maps every exception to an RFC 9457 Problem Details response with a stable {@code code}.
 *
 * <ul>
 *   <li>400 – Bean Validation failures, malformed bodies, invalid parameters</li>
 *   <li>404 – missing resources</li>
 *   <li>409 – business rule violations, optimistic locking and constraint conflicts</li>
 *   <li>500 – anything unexpected (logged, never leaked to the client)</li>
 * </ul>
 * Framework exceptions handled by {@link ResponseEntityExceptionHandler} are enriched with
 * the same extension properties.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Comparator<ValidationError> BY_FIELD_THEN_MESSAGE =
            Comparator.comparing(ValidationError::field).thenComparing(ValidationError::message);

    private final ProblemDetailFactory problems;

    public GlobalExceptionHandler(ProblemDetailFactory problems) {
        this.problems = problems;
    }

    // ------------------------------------------------------------------ domain

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ProblemDetail> handleResourceNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return respond(problems.create(ErrorCode.RESOURCE_NOT_FOUND, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(BusinessRuleException.class)
    ResponseEntity<ProblemDetail> handleBusinessRule(BusinessRuleException ex, HttpServletRequest request) {
        ProblemDetail problem = problems.create(ex.getCode(), ex.getMessage(), request.getRequestURI());
        ex.getProperties().forEach(problem::setProperty);
        return respond(problem);
    }

    // ------------------------------------------------------------------ persistence

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> handleOptimisticLocking(OptimisticLockingFailureException ex,
                                                          HttpServletRequest request) {
        log.debug("Optimistic locking conflict on {}", request.getRequestURI(), ex);
        return respond(problems.create(ErrorCode.CONCURRENT_MODIFICATION,
                "The resource was modified by another request. Reload it and try again.",
                request.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleDataIntegrityViolation(DataIntegrityViolationException ex,
                                                               HttpServletRequest request) {
        String constraint = DatabaseConstraints.constraintName(ex).orElse(null);
        log.warn("Data integrity violation on {} (constraint: {})", request.getRequestURI(), constraint);
        ProblemDetail problem = DatabaseConstraints.errorFor(constraint)
                .map(error -> problems.create(error.code(), error.detail(), request.getRequestURI()))
                .orElseGet(() -> problems.create(ErrorCode.DATA_INTEGRITY_VIOLATION,
                        "The request conflicts with existing data.", request.getRequestURI()));
        return respond(problem);
    }

    @ExceptionHandler(PropertyReferenceException.class)
    ResponseEntity<ProblemDetail> handlePropertyReference(PropertyReferenceException ex, HttpServletRequest request) {
        return respond(problems.create(ErrorCode.INVALID_PARAMETER,
                "Unknown property '%s'.".formatted(ex.getPropertyName()), request.getRequestURI()));
    }

    // ------------------------------------------------------------------ validation outside MVC binding

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ProblemDetail> handleConstraintViolation(ConstraintViolationException ex,
                                                            HttpServletRequest request) {
        List<ValidationError> errors = ex.getConstraintViolations().stream()
                .map(violation -> new ValidationError(leafName(violation.getPropertyPath()), violation.getMessage()))
                .sorted(BY_FIELD_THEN_MESSAGE)
                .toList();
        return respond(problems.validation(errors, request.getRequestURI()));
    }

    // ------------------------------------------------------------------ fallback

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(problems.create(ErrorCode.INTERNAL_ERROR,
                "An unexpected error occurred.", request.getRequestURI()));
    }

    // ------------------------------------------------------------------ Spring MVC exceptions

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<ValidationError> errors = Stream.concat(
                        ex.getBindingResult().getFieldErrors().stream()
                                .map(error -> new ValidationError(error.getField(), error.getDefaultMessage())),
                        ex.getBindingResult().getGlobalErrors().stream()
                                .map(error -> new ValidationError(error.getObjectName(), error.getDefaultMessage())))
                .sorted(BY_FIELD_THEN_MESSAGE)
                .toList();
        return handleExceptionInternal(ex, problems.validation(errors, path(request)), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        List<ValidationError> errors = ex.getAllValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> new ValidationError(parameterName(result), error.getDefaultMessage())))
                .sorted(BY_FIELD_THEN_MESSAGE)
                .toList();
        return handleExceptionInternal(ex, problems.validation(errors, path(request)), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        String detail = "Request body is missing or malformed.";
        if (ex.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            detail = "Invalid or missing value for field '%s'.".formatted(jsonPath(mismatch.getPath()));
        }
        return handleExceptionInternal(ex, problems.create(ErrorCode.MALFORMED_REQUEST, detail, path(request)),
                headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(TypeMismatchException ex, HttpHeaders headers,
                                                        HttpStatusCode status, WebRequest request) {
        String parameter = ex instanceof MethodArgumentTypeMismatchException mismatch
                ? mismatch.getName()
                : ex.getPropertyName();
        String detail = "Invalid value '%s' for parameter '%s'.".formatted(ex.getValue(), parameter);
        return handleExceptionInternal(ex, problems.create(ErrorCode.INVALID_PARAMETER, detail, path(request)),
                headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(MissingServletRequestParameterException ex,
                                                                          HttpHeaders headers,
                                                                          HttpStatusCode status,
                                                                          WebRequest request) {
        String detail = "Required parameter '%s' is missing.".formatted(ex.getParameterName());
        return handleExceptionInternal(ex, problems.create(ErrorCode.INVALID_PARAMETER, detail, path(request)),
                headers, status, request);
    }

    /**
     * Final hook for every response produced by {@link ResponseEntityExceptionHandler}: makes sure
     * framework-generated Problem Details carry the same extension properties as ours.
     */
    @Override
    protected ResponseEntity<Object> createResponseEntity(@Nullable Object body, HttpHeaders headers,
                                                          HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            problems.enrich(problem, path(request));
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    // ------------------------------------------------------------------ helpers

    private static ResponseEntity<ProblemDetail> respond(ProblemDetail problem) {
        return ResponseEntity.status(problem.getStatus()).body(problem);
    }

    @Nullable
    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest servletRequest
                ? servletRequest.getRequest().getRequestURI()
                : null;
    }

    private static String parameterName(ParameterValidationResult result) {
        RequestParam requestParam = result.getMethodParameter().getParameterAnnotation(RequestParam.class);
        if (requestParam != null && !requestParam.name().isEmpty()) {
            return requestParam.name();
        }
        PathVariable pathVariable = result.getMethodParameter().getParameterAnnotation(PathVariable.class);
        if (pathVariable != null && !pathVariable.name().isEmpty()) {
            return pathVariable.name();
        }
        String name = result.getMethodParameter().getParameterName();
        return name != null ? name : "parameter";
    }

    private static String leafName(Path path) {
        return StreamSupport.stream(path.spliterator(), false)
                .reduce((first, second) -> second)
                .map(Path.Node::getName)
                .orElse(path.toString());
    }

    private static String jsonPath(List<Reference> path) {
        return path.stream()
                .map(reference -> reference.getFieldName() != null
                        ? reference.getFieldName()
                        : "[" + reference.getIndex() + "]")
                .collect(Collectors.joining("."))
                .replace(".[", "[");
    }
}
