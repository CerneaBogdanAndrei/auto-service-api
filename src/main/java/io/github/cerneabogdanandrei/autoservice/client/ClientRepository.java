package io.github.cerneabogdanandrei.autoservice.client;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * The native queries reference vehicles and appointments on purpose: those features depend on
 * clients, so the client feature must not depend back on their classes.
 */
public interface ClientRepository extends JpaRepository<Client, Long> {

    @Query("select count(c) > 0 from Client c where lower(c.email) = lower(:email)")
    boolean existsByEmail(String email);

    @Query("select count(c) > 0 from Client c where lower(c.email) = lower(:email) and c.id <> :excludedId")
    boolean existsByEmailAndIdNot(String email, Long excludedId);

    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM appointments a
                           JOIN vehicles v ON v.id = a.vehicle_id
                           WHERE v.client_id = :clientId)""", nativeQuery = true)
    boolean hasVehiclesWithAppointments(Long clientId);

    /** Only valid once {@link #hasVehiclesWithAppointments} is false (appointments are RESTRICT). */
    @Modifying
    @Query(value = "DELETE FROM vehicles WHERE client_id = :clientId", nativeQuery = true)
    int deleteVehiclesWithoutHistory(Long clientId);
}
