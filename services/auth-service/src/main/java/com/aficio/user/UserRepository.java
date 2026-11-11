package com.aficio.user;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<User> findByUsername(String username);

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByVerificationToken(String verificationToken);

    Optional<User> findByGoogleId(String googleId);

    Optional<User> findByListenerCodeIgnoreCase(String listenerCode);

    @Query("""
        SELECT u FROM User u
        WHERE u.enabled = true
          AND u.isPrivate = false
          AND (
              LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%'))
              OR (u.displayName IS NOT NULL AND LOWER(u.displayName) LIKE LOWER(CONCAT('%', :query, '%')))
          )
        ORDER BY
          CASE
            WHEN LOWER(u.username) = LOWER(:query) THEN 0
            WHEN u.displayName IS NOT NULL AND LOWER(u.displayName) = LOWER(:query) THEN 1
            WHEN LOWER(u.username) LIKE LOWER(CONCAT(:query, '%')) THEN 2
            WHEN u.displayName IS NOT NULL AND LOWER(u.displayName) LIKE LOWER(CONCAT(:query, '%')) THEN 3
            ELSE 4
          END,
          u.username ASC
    """)
    List<User> searchPublicUsers(@Param("query") String query);
}
