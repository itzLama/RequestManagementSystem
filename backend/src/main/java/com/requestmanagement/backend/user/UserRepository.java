package com.requestmanagement.backend.user;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findByActiveTrueOrderByFullNameAsc();

    List<User> findByRoleOrderByFullNameAscIdAsc(User.Role role);

    Optional<User> findByIdAndRole(Long id, User.Role role);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
}
