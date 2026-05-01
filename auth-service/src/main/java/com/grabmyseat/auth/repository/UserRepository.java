package com.grabmyseat.auth.repository;

import com.grabmyseat.auth.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);


    Page<User> findByUsernameContainingIgnoreCase(String username, Pageable pageable);
    boolean existsByUsername(String username);
}
