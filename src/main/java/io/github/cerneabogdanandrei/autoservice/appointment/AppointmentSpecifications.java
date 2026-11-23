package io.github.cerneabogdanandrei.autoservice.appointment;

import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

/**
 * Optional list filters; a {@code null} argument means "no restriction".
 */
final class AppointmentSpecifications {

    private AppointmentSpecifications() {
    }

    static Specification<Appointment> hasStatus(AppointmentStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    static Specification<Appointment> forVehicle(Long vehicleId) {
        return (root, query, cb) -> vehicleId == null ? null : cb.equal(root.get("vehicle").get("id"), vehicleId);
    }

    static Specification<Appointment> startsAtOrAfter(Instant from) {
        return (root, query, cb) -> from == null ? null : cb.greaterThanOrEqualTo(root.get("startAt"), from);
    }

    static Specification<Appointment> startsBefore(Instant to) {
        return (root, query, cb) -> to == null ? null : cb.lessThan(root.get("startAt"), to);
    }
}
