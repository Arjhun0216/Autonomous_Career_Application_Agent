package com.jobscraper.job_platform.profile;

import com.jobscraper.job_platform.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;


    // ============================================================
    // UPDATE PROFILE
    // ============================================================

    @PutMapping("/me")
    public ResponseEntity<?> updateProfile(
            @RequestBody ProfileUpdateRequest request,
            Authentication authentication) {

        try {

            String email = authentication.getName();

            User user =
                    profileService.updateProfile(
                            email,
                            request
                    );

            return ResponseEntity.ok(
                    new ProfileResponse(
                            user.getUserId(),
                            user.getEmail(),
                            user.isRegistrationCompleted()
                    )
            );

        } catch (RuntimeException e) {

            return ResponseEntity
                    .badRequest()
                    .body(e.getMessage());
        }
    }


    // ============================================================
    // GET CURRENT PROFILE
    // ============================================================

    @GetMapping("/me")
    public ResponseEntity<?> getProfile(
            Authentication authentication) {

        try {

            String email = authentication.getName();

            User user =
                    profileService.getProfile(email);

            return ResponseEntity.ok(user);

        } catch (RuntimeException e) {

            return ResponseEntity
                    .badRequest()
                    .body(e.getMessage());
        }
    }


    // ============================================================
    // RESPONSE
    // ============================================================

    public record ProfileResponse(
            Long userId,
            String email,
            boolean profileCompleted
    ) {
    }
}