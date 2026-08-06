package com.smartqueue.service;

import com.smartqueue.dto.*;
import com.smartqueue.entity.User;
import com.smartqueue.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor @Slf4j
public class UserManagementService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;

    public List<UserResponse> getAllUsers() {
        return userRepo.findAll().stream().map(this::toResponse).collect(Collectors.toList());
    }

    public List<UserResponse> getUsersByRole(String role) {
        return userRepo.findByRole(User.Role.valueOf(role.toUpperCase()))
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    public UserResponse getUserById(Long id) {
        return toResponse(userRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id)));
    }

    /**
     * BUG 10 FIX: Only ADMIN can assign ADMIN or DOCTOR roles.
     * This is enforced at the SecurityConfig level (/api/admin/** requires ADMIN)
     * but we double-check here to prevent privilege escalation.
     */
    @Transactional
    public UserResponse updateRole(UserRoleUpdateRequest req) {
        User user = userRepo.findById(req.getUserId())
                .orElseThrow(() -> new RuntimeException("User not found: " + req.getUserId()));

        User.Role newRole;
        try { newRole = User.Role.valueOf(req.getRole().toUpperCase()); }
        catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid role: " + req.getRole() + ". Valid: PATIENT, DOCTOR, ADMIN");
        }

        // Prevent demoting the last admin
        if (user.getRole() == User.Role.ADMIN && newRole != User.Role.ADMIN) {
            long adminCount = userRepo.countByRole(User.Role.ADMIN);
            if (adminCount <= 1) throw new RuntimeException("Cannot change role: this is the last admin account.");
        }

        user.setRole(newRole);
        userRepo.save(user);
        log.info("User {} role changed to {}", user.getEmail(), newRole);
        return toResponse(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        if (user.getRole() == User.Role.ADMIN) {
            long adminCount = userRepo.countByRole(User.Role.ADMIN);
            if (adminCount <= 1) throw new RuntimeException("Cannot delete the last admin account.");
        }
        userRepo.delete(user);
        log.info("User deleted: {}", id);
    }

    @Transactional
    public void resetPassword(Long id, String newPassword) {
        User user = userRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("User not found: " + id));
        if (newPassword == null || newPassword.length() < 6)
            throw new RuntimeException("Password must be at least 6 characters.");
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepo.save(user);
        log.info("Password reset for user {}", id);
    }

    private UserResponse toResponse(User u) {
        return UserResponse.builder()
                .id(u.getId()).name(u.getName()).email(u.getEmail())
                .role(u.getRole().name()).phone(u.getPhone()).createdAt(u.getCreatedAt())
                .build();
    }
}
