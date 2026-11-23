package io.github.cerneabogdanandrei.autoservice.vehicle;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ModelYearValidatorTest {

    private final ModelYearValidator validator =
            new ModelYearValidator(Clock.fixed(Instant.parse("2030-06-15T10:00:00Z"), ZoneOffset.UTC));

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "1885, false",
            "1886, true",
            "2019, true",
            "2030, true",
            "2031, true",
            "2032, false",
            "null, true"
    })
    void acceptsYearsFromFirstAutomobileUntilNextYear(Integer year, boolean valid) {
        assertThat(validator.isValid(year, null)).isEqualTo(valid);
    }
}
