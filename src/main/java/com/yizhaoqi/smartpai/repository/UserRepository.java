package com.yizhaoqi.smartpai.repository;

import com.yizhaoqi.smartpai.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);


    @Query("SELECT u FROM User u WHERE u.username = :username")
    Optional<User> getInfoByName(@Param("username") String username);
}
