package io.github.cerneabogdanandrei.autoservice.intervention;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface InterventionRepository extends JpaRepository<Intervention, Long>, JpaSpecificationExecutor<Intervention> {

    /**
     * Lists show the assignee, fetched in the same query. Part lines are not loaded here:
     * fetching a collection together with paging would page in memory.
     */
    @Override
    @EntityGraph(attributePaths = "assignedUser")
    Page<Intervention> findAll(Specification<Intervention> specification, Pageable pageable);

    /** Everything a detail response needs, in one query. */
    @EntityGraph(attributePaths = {"assignedUser", "parts", "parts.part"})
    Optional<Intervention> findDetailedById(Long id);

    /** Reads only the appointment id, so the appointment can be locked before the intervention. */
    @Query("select i.appointment.id from Intervention i where i.id = :id")
    Optional<Long> findAppointmentIdById(Long id);

    /** {@code SELECT ... FOR UPDATE}: changes to one intervention (and its part lines) are serialized. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Intervention i where i.id = :id")
    Optional<Intervention> findByIdForUpdate(Long id);
}
