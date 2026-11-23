package io.github.cerneabogdanandrei.autoservice.intervention;

import org.springframework.data.jpa.domain.Specification;

/**
 * Optional list filters; a {@code null} argument means "no restriction".
 */
final class InterventionSpecifications {

    private InterventionSpecifications() {
    }

    static Specification<Intervention> forAppointment(Long appointmentId) {
        return (root, query, cb) -> appointmentId == null ? null : cb.equal(root.get("appointment").get("id"), appointmentId);
    }

    static Specification<Intervention> assignedTo(Long userId) {
        return (root, query, cb) -> userId == null ? null : cb.equal(root.get("assignedUser").get("id"), userId);
    }
}
