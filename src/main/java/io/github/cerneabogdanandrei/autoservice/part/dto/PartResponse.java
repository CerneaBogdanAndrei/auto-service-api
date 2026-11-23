package io.github.cerneabogdanandrei.autoservice.part.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PartResponse(
        Long id,
        String code,
        String name,
        BigDecimal unitPrice,
        Integer stock,
        Instant createdAt,
        Instant updatedAt) {
}
