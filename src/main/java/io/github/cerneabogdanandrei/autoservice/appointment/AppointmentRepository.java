package io.github.cerneabogdanandrei.autoservice.appointment;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, Long>, JpaSpecificationExecutor<Appointment> {

    /** The vehicle is shown in every response, so it is fetched in the same query (no N+1). */
    @Override
    @EntityGraph(attributePaths = "vehicle")
    Page<Appointment> findAll(Specification<Appointment> specification, Pageable pageable);

    @EntityGraph(attributePaths = "vehicle")
    Optional<Appointment> findWithVehicleById(Long id);

    /** Half-open intervals overlap when each one starts before the other ends. */
    @Query("""
            select count(a) > 0 from Appointment a
            where a.vehicle.id = :vehicleId
              and a.status <> io.github.cerneabogdanandrei.autoservice.appointment.AppointmentStatus.CANCELLED
              and a.startAt < :endAt and a.endAt > :startAt""")
    boolean existsOverlapping(Long vehicleId, Instant startAt, Instant endAt);

    @Query("""
            select count(a) > 0 from Appointment a
            where a.vehicle.id = :vehicleId
              and a.id <> :excludedId
              and a.status <> io.github.cerneabogdanandrei.autoservice.appointment.AppointmentStatus.CANCELLED
              and a.startAt < :endAt and a.endAt > :startAt""")
    boolean existsOverlappingExcluding(Long vehicleId, Instant startAt, Instant endAt, Long excludedId);

    // The cost queries are native on purpose: interventions depend on appointments, so the
    // appointment feature reads their rows without depending on intervention classes.

    @Query(value = "SELECT labor_cost FROM interventions WHERE appointment_id = :appointmentId", nativeQuery = true)
    List<BigDecimal> findLaborCosts(Long appointmentId);

    @Query(value = """
            SELECT ip.quantity AS quantity, ip.unit_price AS unitPrice
            FROM intervention_parts ip
            JOIN interventions i ON i.id = ip.intervention_id
            WHERE i.appointment_id = :appointmentId""", nativeQuery = true)
    List<PartLineView> findPartLines(Long appointmentId);

    interface PartLineView {
        Integer getQuantity();

        BigDecimal getUnitPrice();
    }
}
