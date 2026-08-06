package com.smartqueue.repository;

import com.smartqueue.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByPhone(String phone);
    List<User> findByRole(User.Role role);
    long countByRole(User.Role role);

    @Query("SELECT u FROM User u WHERE LOWER(u.name) LIKE LOWER(CONCAT('%',:q,'%')) " +
           "OR LOWER(u.email) LIKE LOWER(CONCAT('%',:q,'%'))")
    List<User> searchByNameOrEmail(@Param("q") String query);
}
