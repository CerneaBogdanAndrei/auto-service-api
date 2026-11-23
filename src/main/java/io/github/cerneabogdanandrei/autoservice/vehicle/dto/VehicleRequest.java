package io.github.cerneabogdanandrei.autoservice.vehicle.dto;

import io.github.cerneabogdanandrei.autoservice.vehicle.ModelYear;
import io.github.cerneabogdanandrei.autoservice.vehicle.Vin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Used for both create and full update (PUT); changing {@code clientId} transfers the vehicle.
 */
public record VehicleRequest(
        @NotNull Long clientId,

        @NotBlank @Vin String vin,

        @NotBlank
        @Pattern(regexp = "^[A-Za-z0-9 -]{2,15}$", message = "must be 2-15 letters, digits, spaces or dashes")
        String licensePlate,

        @NotBlank @Size(max = 50) String make,

        @NotBlank @Size(max = 50) String model,

        @NotNull @ModelYear Integer manufactureYear) {
}
