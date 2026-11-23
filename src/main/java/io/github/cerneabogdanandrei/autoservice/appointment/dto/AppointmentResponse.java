package io.github.cerneabogdanandrei.autoservice.appointment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.cerneabogdanandrei.autoservice.appointment.AppointmentCost;
import io.github.cerneabogdanandrei.autoservice.appointment.AppointmentStatus;

import java.time.Instant;

/**
 * @param cost included when a single appointment is returned; omitted in lists
 */
public record AppointmentResponse(
        Long id,
        VehicleSummary vehicle,
        Instant startAt,
        Instant endAt,
        AppointmentStatus status,
        String notes,
        Instant completedAt,
        @JsonInclude(JsonInclude.Include.NON_NULL) AppointmentCost cost,
        Instant createdAt,
        Instant updatedAt) {

    public record VehicleSummary(Long id, String vin, String licensePlate, String make, String model) {
    }
}
