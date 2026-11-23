package io.github.cerneabogdanandrei.autoservice.auth;

import io.github.cerneabogdanandrei.autoservice.common.exception.ErrorCode;
import io.github.cerneabogdanandrei.autoservice.common.exception.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 403 raised by the filter chain. (403s raised by method security inside controllers are
 * handled by the MVC exception handler with the same body.)
 */
@Component
class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemDetailFactory problems;
    private final ProblemDetailResponseWriter writer;

    ProblemDetailAccessDeniedHandler(ProblemDetailFactory problems, ProblemDetailResponseWriter writer) {
        this.problems = problems;
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        writer.write(response, problems.create(ErrorCode.ACCESS_DENIED,
                ErrorCode.ACCESS_DENIED_DETAIL, request.getRequestURI()));
    }
}
