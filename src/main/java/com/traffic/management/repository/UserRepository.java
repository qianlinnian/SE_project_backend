package com.traffic.management.repository;

import com.traffic.management.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户Repository
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * 根据用户名查找用户
     */
    Optional<User> findByUsername(String username);

    /**
     * 检查用户名是否存在
     */
    boolean existsByUsername(String username);

    /**
     * 检查警号是否存在
     */
    boolean existsByPoliceNumber(String policeNumber);

    /**
     * 根据角色分页查询用户
     */
    Page<User> findByRole(User.UserRole role, Pageable pageable);

    /**
     * 根据角色和状态分页查询用户
     */
    Page<User> findByRoleAndStatus(User.UserRole role, User.UserStatus status, Pageable pageable);
}
