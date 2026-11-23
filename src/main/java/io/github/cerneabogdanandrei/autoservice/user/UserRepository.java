package io.github.cerneabogdanandrei.autoservice.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    /** Case-insensitive lookup; matches the {@code lower(username)} unique index. */
    @Query("select u from User u where lower(u.username) = lower(:username)")
    Optional<User> findByUsername(String username);

    @Query("select count(u) > 0 from User u where lower(u.username) = lower(:username)")
    boolean existsByUsername(String username);

    /**
     * Native query on purpose: the intervention feature depends on users (assignee), so the
     * user feature must not depend back on intervention classes.
     */
    @Query(value = "SELECT EXISTS (SELECT 1 FROM interventions WHERE assigned_user_id = :userId)", nativeQuery = true)
    boolean hasAssignedInterventions(Long userId);
}
