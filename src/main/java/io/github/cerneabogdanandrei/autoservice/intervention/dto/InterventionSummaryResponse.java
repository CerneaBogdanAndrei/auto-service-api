package io.github.cerneabogdanandrei.autoservice.intervention.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * List item; part lines and totals are available on the detail endpoint.
 */
public record InterventionSummaryResponse(
        Long id,
        Long appointmentId,
        InterventionResponse.AssigneeSummary assignee,
        String description,
        BigDecimal laborCost,
        Instant createdAt,
        Instant updatedAt) {
}
