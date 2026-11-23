package io.github.cerneabogdanandrei.autoservice.part.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Used for both create and full update (PUT).
 */
public record PartRequest(
        @NotBlank
        @Size(max = 30)
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "may contain only letters, digits, '.', '_' and '-'")
        String code,

        @NotBlank @Size(max = 100) String name,

        @NotNull @DecimalMin("0.00") @Digits(integer = 10, fraction = 2) BigDecimal unitPrice,

        @NotNull @PositiveOrZero Integer stock) {
}
