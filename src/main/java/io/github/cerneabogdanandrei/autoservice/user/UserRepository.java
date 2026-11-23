package io.github.cerneabogdanandrei.autoservice.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** Case-insensitive lookup; matches the {@code lower(username)} unique index. */
    @Query("select u from User u where lower(u.username) = lower(:username)")
    Optional<User> findByUsername(String username);
}
