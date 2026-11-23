package io.github.cerneabogdanandrei.autoservice.intervention.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * @param assignedUserId optional; only ADMIN and OWNER may reassign an intervention
 */
public record InterventionUpdateRequest(
        Long assignedUserId,
        @NotBlank @Size(max = 1000) String description,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal laborCost) {
}
