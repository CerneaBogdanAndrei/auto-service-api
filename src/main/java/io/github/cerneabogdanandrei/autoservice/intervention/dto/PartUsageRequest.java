package io.github.cerneabogdanandrei.autoservice.intervention.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PartUsageRequest(
        @NotNull Long partId,
        @NotNull @Positive @Max(10_000) Integer quantity) {
}
