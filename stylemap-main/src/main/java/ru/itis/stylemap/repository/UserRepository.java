package ru.itis.stylemap.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.itis.stylemap.entity.User;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    @Transactional(readOnly = true)
    @Query("select count(u) from User u where u.isActive = true")
    long countActiveUsers();

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByExternalAuthId(String externalAuthId);
}
