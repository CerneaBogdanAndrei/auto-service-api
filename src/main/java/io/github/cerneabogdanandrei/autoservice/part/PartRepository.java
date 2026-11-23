package io.github.cerneabogdanandrei.autoservice.part;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface PartRepository extends JpaRepository<Part, Long> {

    Page<Part> findByStockLessThan(int threshold, Pageable pageable);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long excludedId);

    /**
     * Locks the rows ({@code SELECT ... FOR UPDATE}) so concurrent stock changes are serialized.
     * Rows are always locked in id order, so two transactions touching the same parts cannot
     * deadlock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Part p where p.id in :ids order by p.id")
    List<Part> findAllByIdForUpdate(Collection<Long> ids);

    /**
     * Native on purpose: interventions depend on parts, so the part feature must not depend back
     * on intervention classes.
     */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM intervention_parts WHERE part_id = :partId)", nativeQuery = true)
    boolean isUsedInInterventions(Long partId);
}
