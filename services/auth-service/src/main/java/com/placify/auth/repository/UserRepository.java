package com.placify.auth.repository;

import com.placify.auth.domain.User;
import com.placify.auth.domain.UserStatus;
import com.placify.common.security.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByLoginId(String loginId);

    Optional<User> findByLoginIdAndRole(String loginId, Role role);

    boolean existsByLoginId(String loginId);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByRole(Role role);

    List<User> findByRoleIn(Collection<Role> roles);

    List<User> findByRoleAndStatus(Role role, UserStatus status);

    List<User> findByIdIn(Collection<String> ids);

    long countByRole(Role role);

    long countByRoleAndStatus(Role role, UserStatus status);

    @Query("""
            select u from User u
            where (:role is null or u.role = :role)
              and (:status is null or u.status = :status)
              and (:q is null or lower(u.name) like lower(concat('%', :q, '%'))
                   or lower(u.loginId) like lower(concat('%', :q, '%'))
                   or lower(u.email) like lower(concat('%', :q, '%')))
            """)
    Page<User> search(@Param("role") Role role,
                      @Param("status") UserStatus status,
                      @Param("q") String q,
                      Pageable pageable);
}
