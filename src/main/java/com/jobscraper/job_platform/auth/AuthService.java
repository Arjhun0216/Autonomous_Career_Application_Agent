package com.jobscraper.job_platform.auth;

import com.jobscraper.job_platform.dto.AuthResponse;
import com.jobscraper.job_platform.dto.LoginRequest;
import com.jobscraper.job_platform.dto.RegisterRequest;
import com.jobscraper.job_platform.security.JwtService;
import com.jobscraper.job_platform.user.User;
import com.jobscraper.job_platform.user.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;

    public AuthResponse register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException(
                    "Email already registered"
            );
        }

        if (userRepository.existsByPhone(request.getPhone())) {
            throw new RuntimeException(
                    "Phone number already registered"
            );
        }

       User user = User.builder()
        .email(request.getEmail())
        .phone(request.getPhone())
        .passwordHash(
                passwordEncoder.encode(
                        request.getPassword()
                )
        )
        .authProvider("LOCAL")
        .userExperience(0)
        .registrationCompleted(false)
        .build();

        User savedUser = userRepository.save(user);

        String token =
                jwtService.generateToken(
                        savedUser.getEmail()
                );

        return new AuthResponse(
                token,
                savedUser.getUserId(),
                savedUser.getEmail(),
                savedUser.isRegistrationCompleted()
        );
    }

    public AuthResponse login(LoginRequest request) {

        User user = userRepository
                .findByEmail(request.getEmail())
                .orElseThrow(() ->
                        new RuntimeException(
                                "Invalid email or password"
                        )
                );

        if (user.getPasswordHash() == null ||
                !passwordEncoder.matches(
                        request.getPassword(),
                        user.getPasswordHash()
                )) {

            throw new RuntimeException(
                    "Invalid email or password"
            );
        }

        String token =
                jwtService.generateToken(
                        user.getEmail()
                );

        return new AuthResponse(
                token,
                user.getUserId(),
                user.getEmail(),
                user.isRegistrationCompleted()
        );
    }
}