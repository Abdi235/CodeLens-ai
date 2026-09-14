package com.secureai.repository;

import com.secureai.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    Optional<User> findByUsernameIgnoreCase(String username);

    boolean existsByEmail(String email);

    boolean existsByUsernameIgnoreCase(String username);

    @Query("""
            select u from User u
            where lower(u.email) = lower(:login)
               or lower(u.username) = lower(:login)
            """)
    Optional<User> findByLogin(@Param("login") String login);
}
