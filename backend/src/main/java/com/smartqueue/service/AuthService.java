package com.smartqueue.service;

import com.smartqueue.dto.*;
import com.smartqueue.entity.User;
import com.smartqueue.repository.UserRepository;
import com.smartqueue.security.JwtUtil;
import com.smartqueue.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepo;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authManager;
    private final JwtUtil jwtUtil;

    @Transactional
    public AuthResponse register(RegisterRequest req) {

        if (userRepo.existsByEmail(req.getEmail())) {
            throw new IllegalArgumentException("Email already registered: " + req.getEmail());
        }

        User user = User.builder()
                .name(req.getName())
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .phone(req.getPhone())
                .role(User.Role.PATIENT)
                .build();

        userRepo.save(user);

        log.info("New PATIENT registered: {}", req.getEmail());

        return buildAuthResponse(new UserPrincipal(user));
    }

    public AuthResponse login(LoginRequest req) {

        Authentication authentication = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        req.getEmail(),
                        req.getPassword()));

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = principal.getUser();

        log.info("Login: {} ({})", user.getEmail(), user.getRole());

        return buildAuthResponse(principal);
    }

    public AuthResponse getMe(String email) {

        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return buildAuthResponse(new UserPrincipal(user));
    }

    private AuthResponse buildAuthResponse(UserPrincipal principal) {

        User user = principal.getUser();

        String token = jwtUtil.generateToken(principal);

        return AuthResponse.builder()
                .token(token)
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
}