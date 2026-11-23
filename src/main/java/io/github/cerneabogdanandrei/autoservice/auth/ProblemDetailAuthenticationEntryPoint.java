package io.github.cerneabogdanandrei.autoservice.auth;

import io.github.cerneabogdanandrei.autoservice.common.exception.ErrorCode;
import io.github.cerneabogdanandrei.autoservice.common.exception.ProblemDetailFactory;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 401 for missing, malformed, expired or forged tokens. The standard bearer entry point
 * sets the status and the RFC 6750 {@code WWW-Authenticate} header; the body is a Problem Detail.
 */
@Component
class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final AuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();
    private final ProblemDetailFactory problems;
    private final ProblemDetailResponseWriter writer;

    ProblemDetailAuthenticationEntryPoint(ProblemDetailFactory problems, ProblemDetailResponseWriter writer) {
        this.problems = problems;
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException, ServletException {
        bearerEntryPoint.commence(request, response, exception);
        String detail = exception instanceof OAuth2AuthenticationException
                ? "The access token is invalid or has expired."
                : "Authentication is required to access this resource.";
        writer.write(response, problems.create(ErrorCode.UNAUTHORIZED, detail, request.getRequestURI()));
    }
}
