package io.github.cerneabogdanandrei.autoservice.common.exception;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatusCode;

import static org.assertj.core.api.Assertions.assertThat;

class ErrorCodeTest {

    @ParameterizedTest
    @CsvSource({
            "400, MALFORMED_REQUEST",
            "401, UNAUTHORIZED",
            "403, ACCESS_DENIED",
            "404, RESOURCE_NOT_FOUND",
            "405, METHOD_NOT_ALLOWED",
            "406, NOT_ACCEPTABLE",
            "409, DATA_INTEGRITY_VIOLATION",
            "413, REQUEST_REJECTED",
            "415, UNSUPPORTED_MEDIA_TYPE",
            "500, INTERNAL_ERROR",
            "503, INTERNAL_ERROR"
    })
    void mapsFrameworkStatusesToCodes(int status, ErrorCode expected) {
        assertThat(ErrorCode.fromStatus(HttpStatusCode.valueOf(status))).isEqualTo(expected);
    }
}
