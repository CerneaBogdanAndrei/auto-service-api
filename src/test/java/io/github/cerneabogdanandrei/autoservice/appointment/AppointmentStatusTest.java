package io.github.cerneabogdanandrei.autoservice.appointment;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AppointmentStatusTest {

    /** The complete transition matrix: every (from, to) pair, including staying in place. */
    @ParameterizedTest(name = "{0} -> {1} allowed: {2}")
    @CsvSource({
            "SCHEDULED,   SCHEDULED,   false",
            "SCHEDULED,   IN_PROGRESS, true",
            "SCHEDULED,   COMPLETED,   false",
            "SCHEDULED,   CANCELLED,   true",
            "IN_PROGRESS, SCHEDULED,   false",
            "IN_PROGRESS, IN_PROGRESS, false",
            "IN_PROGRESS, COMPLETED,   true",
            "IN_PROGRESS, CANCELLED,   false",
            "COMPLETED,   SCHEDULED,   false",
            "COMPLETED,   IN_PROGRESS, false",
            "COMPLETED,   COMPLETED,   false",
            "COMPLETED,   CANCELLED,   false",
            "CANCELLED,   SCHEDULED,   false",
            "CANCELLED,   IN_PROGRESS, false",
            "CANCELLED,   COMPLETED,   false",
            "CANCELLED,   CANCELLED,   false"
    })
    void transitionMatrix(AppointmentStatus from, AppointmentStatus to, boolean allowed) {
        assertThat(from.canTransitionTo(to)).isEqualTo(allowed);
    }
}
