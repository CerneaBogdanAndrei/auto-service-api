package io.github.cerneabogdanandrei.autoservice.vehicle;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    /** The owning client is shown in every response, so it is fetched in the same query (no N+1). */
    @Override
    @EntityGraph(attributePaths = "client")
    Page<Vehicle> findAll(Pageable pageable);

    @EntityGraph(attributePaths = "client")
    Page<Vehicle> findByClientId(Long clientId, Pageable pageable);

    @EntityGraph(attributePaths = "client")
    Optional<Vehicle> findWithClientById(Long id);

    boolean existsByVin(String vin);

    boolean existsByVinAndIdNot(String vin, Long excludedId);

    /**
     * Native on purpose: appointments depend on vehicles, so the vehicle feature must not depend
     * back on appointment classes.
     */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM appointments WHERE vehicle_id = :vehicleId)", nativeQuery = true)
    boolean hasAppointments(Long vehicleId);
}
