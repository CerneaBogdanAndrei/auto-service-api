package io.github.cerneabogdanandrei.autoservice.vehicle.dto;

import java.time.Instant;

public record VehicleResponse(
        Long id,
        String vin,
        String licensePlate,
        String make,
        String model,
        Integer manufactureYear,
        ClientSummary client,
        Instant createdAt,
        Instant updatedAt) {

    public record ClientSummary(Long id, String name) {
    }
}
