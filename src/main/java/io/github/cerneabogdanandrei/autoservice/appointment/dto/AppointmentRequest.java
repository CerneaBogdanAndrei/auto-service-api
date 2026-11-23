package io.github.cerneabogdanandrei.autoservice.appointment.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * Used for both create and reschedule (PUT). Times are ISO-8601 with an offset,
 * e.g. {@code 2026-12-01T09:00:00+02:00}; the interval is half-open [startAt, endAt).
 */
public record AppointmentRequest(
        @NotNull Long vehicleId,

        @NotNull @Schema(example = "2026-12-01T09:00:00+02:00") OffsetDateTime startAt,

        @NotNull @Schema(example = "2026-12-01T11:00:00+02:00") OffsetDateTime endAt,

        @Size(max = 1000) String notes) {

    @JsonIgnore
    @AssertTrue(message = "endAt must be after startAt")
    public boolean isEndAfterStart() {
        return startAt == null || endAt == null || endAt.isAfter(startAt);
    }
}
