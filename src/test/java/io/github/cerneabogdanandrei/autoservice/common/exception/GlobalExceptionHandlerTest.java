package io.github.cerneabogdanandrei.autoservice.common.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.data.util.TypeInformation;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// controllers = ... keeps the real application controllers out of this slice;
// the nested test controller itself is not picked up by scanning, so it is imported.
@WebMvcTest(controllers = GlobalExceptionHandlerTest.SampleController.class)
@Import({
        GlobalExceptionHandlerTest.SampleController.class,
        ProblemDetailFactory.class,
        GlobalExceptionHandlerTest.FixedClockConfig.class
})
class GlobalExceptionHandlerTest {

    private static final Instant NOW = Instant.parse("2030-01-15T10:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Test
    void resourceNotFoundReturns404Problem() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Not Found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.detail").value("Widget with id 42 was not found"))
                .andExpect(jsonPath("$.instance").value("/test/not-found"))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.timestamp").value(NOW.toString()));
    }

    @Test
    void businessRuleViolationReturnsItsStatusCodeAndProperties() throws Exception {
        mockMvc.perform(get("/test/business-rule"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.code").value("DATA_INTEGRITY_VIOLATION"))
                .andExpect(jsonPath("$.detail").value("Rule broken"))
                .andExpect(jsonPath("$.available").value(3));
    }

    @Test
    void invalidRequestBodyReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": " ", "email": "not-an-email", "quantity": 5}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Request validation failed."))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.errors[1].field").value("name"))
                .andExpect(jsonPath("$.errors[1].message").value("must not be blank"));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Request body is missing or malformed."));
    }

    @Test
    void wronglyTypedFieldReturns400NamingTheField() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Widget", "email": "a@example.com", "quantity": "lots"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Invalid or missing value for field 'quantity'."));
    }

    @Test
    void invalidPathVariableTypeReturns400() throws Exception {
        mockMvc.perform(get("/test/items/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.detail").value("Invalid value 'abc' for parameter 'id'."));
    }

    @Test
    void constrainedRequestParameterReturns400() throws Exception {
        mockMvc.perform(get("/test/params").param("threshold", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("threshold"))
                .andExpect(jsonPath("$.errors[0].message").value("must be greater than or equal to 1"));
    }

    @Test
    void missingRequestParameterReturns400() throws Exception {
        mockMvc.perform(get("/test/params"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.detail").value("Required parameter 'threshold' is missing."));
    }

    @Test
    void unknownSortPropertyReturns400() throws Exception {
        mockMvc.perform(get("/test/property-reference"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.detail").value("Unknown property 'colour'."));
    }

    @Test
    void constraintViolationReturns400WithLeafFieldName() throws Exception {
        mockMvc.perform(get("/test/constraint-violation"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be blank"));
    }

    @Test
    void optimisticLockingFailureReturns409() throws Exception {
        mockMvc.perform(get("/test/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void unknownDataIntegrityViolationReturns409WithoutLeakingSql() throws Exception {
        mockMvc.perform(get("/test/data-integrity"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DATA_INTEGRITY_VIOLATION"))
                .andExpect(jsonPath("$.detail").value("The request conflicts with existing data."))
                .andExpect(content().string(not(containsString("uq_widgets_name"))));
    }

    @Test
    void unexpectedExceptionReturns500WithoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andExpect(content().string(not(containsString("secret internals"))));
    }

    @Test
    void unsupportedMethodReturns405Problem() throws Exception {
        mockMvc.perform(put("/test/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.instance").value("/test/not-found"))
                .andExpect(jsonPath("$.timestamp").value(NOW.toString()));
    }

    @Test
    void unsupportedMediaTypeReturns415Problem() throws Exception {
        mockMvc.perform(post("/test/body").contentType(MediaType.TEXT_PLAIN).content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    // ------------------------------------------------------------------ test fixtures

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }

    record SampleRequest(@NotBlank String name, @NotBlank @Email String email, @NotNull Integer quantity) {
    }

    @RestController
    @RequestMapping("/test")
    static class SampleController {

        @GetMapping("/not-found")
        String notFound() {
            throw new ResourceNotFoundException("Widget", 42);
        }

        @GetMapping("/business-rule")
        String businessRule() {
            throw new BusinessRuleException(ErrorCode.DATA_INTEGRITY_VIOLATION, "Rule broken", Map.of("available", 3));
        }

        @PostMapping("/body")
        String body(@Valid @RequestBody SampleRequest request) {
            return "ok";
        }

        @GetMapping("/items/{id}")
        String item(@PathVariable long id) {
            return "ok";
        }

        @GetMapping("/params")
        String params(@RequestParam @Min(1) int threshold) {
            return "ok";
        }

        @GetMapping("/property-reference")
        String propertyReference() {
            throw new PropertyReferenceException("colour", TypeInformation.of(SampleRequest.class), List.of());
        }

        @GetMapping("/constraint-violation")
        String constraintViolation() {
            try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
                Validator validator = factory.getValidator();
                throw new ConstraintViolationException(validator.validate(new SampleRequest(" ", "a@example.com", 1)));
            }
        }

        @GetMapping("/optimistic-lock")
        String optimisticLock() {
            throw new ObjectOptimisticLockingFailureException(SampleRequest.class, 1L);
        }

        @GetMapping("/data-integrity")
        String dataIntegrity() {
            throw new DataIntegrityViolationException("could not execute statement",
                    new SQLException("ERROR: duplicate key value violates unique constraint \"uq_widgets_name\""));
        }

        @GetMapping("/boom")
        String boom() {
            throw new IllegalStateException("secret internals");
        }
    }
}
