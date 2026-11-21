package org.example.sublearn.repository;

import org.example.sublearn.domain.AppUser;
import org.example.sublearn.domain.UserRole;
import org.example.sublearn.domain.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByEmail(String email);

    long countByRole(UserRole role);

    long countByRoleAndStatus(UserRole role, UserStatus status);

    long countByStatus(UserStatus status);

    long countByCreatedAtAfter(Instant after);

    @Query("""
            SELECT u FROM AppUser u
            WHERE (:search IS NULL OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                   OR (u.displayName IS NOT NULL AND LOWER(u.displayName) LIKE LOWER(CONCAT('%', :search, '%'))))
            AND (:role IS NULL OR u.role = :role)
            AND (:status IS NULL OR u.status = :status)
            """)
    Page<AppUser> searchAdminUsers(@Param("search") String search,
                                   @Param("role") UserRole role,
                                   @Param("status") UserStatus status,
                                   Pageable pageable);
}
