package io.github.cerneabogdanandrei.autoservice.intervention.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * @param partsCost Σ quantity × unit price of the lines
 * @param total     laborCost + partsCost
 */
public record InterventionResponse(
        Long id,
        Long appointmentId,
        AssigneeSummary assignee,
        String description,
        BigDecimal laborCost,
        List<PartLine> parts,
        BigDecimal partsCost,
        BigDecimal total,
        Instant createdAt,
        Instant updatedAt) {

    public record AssigneeSummary(Long id, String username, String fullName) {
    }

    public record PartLine(Long partId, String code, String name, Integer quantity, BigDecimal unitPrice,
                           BigDecimal lineTotal) {
    }
}
