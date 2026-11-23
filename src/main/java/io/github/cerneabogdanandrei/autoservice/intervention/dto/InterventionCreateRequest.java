package io.github.cerneabogdanandrei.autoservice.intervention.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/**
 * @param parts optional parts used straight away; all of them are taken from stock in one
 *              transaction, or none of them is
 */
public record InterventionCreateRequest(
        @NotNull Long appointmentId,
        @NotNull Long assignedUserId,
        @NotBlank @Size(max = 1000) String description,
        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal laborCost,
        @Size(max = 50) List<@Valid @NotNull PartUsageRequest> parts) {

    public List<PartUsageRequest> partsOrEmpty() {
        return parts == null ? List.of() : parts;
    }
}
