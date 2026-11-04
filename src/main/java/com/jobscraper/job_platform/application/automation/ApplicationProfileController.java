package com.jobscraper.job_platform.application.automation;

import com.jobscraper.job_platform.user.User;
import com.jobscraper.job_platform.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/application-profile")
@RequiredArgsConstructor
public class ApplicationProfileController {

    private final UserRepository userRepository;
    private final ApplicationProfileService profileService;

    /**
     * Returns application-specific questions discovered during the last
     * application attempt.
     */
    @GetMapping("/missing")
    public ResponseEntity<?> getMissingDetails(
            Authentication authentication
    ) {
        User user = getUser(authentication);

        return ResponseEntity.ok(
                profileService.getMissingDetails(user)
        );
    }

    /**
     * Saves an answer for a previously discovered application question.
     *
     * Example:
     * {
     *   "key": "work_authorization",
     *   "answer": "Yes"
     * }
     */
    @PutMapping("/answer")
    public ResponseEntity<?> saveAnswer(
            @RequestBody AnswerRequest request,
            Authentication authentication
    ) {
        if (request == null
                || request.key() == null
                || request.key().isBlank()) {
            return ResponseEntity.badRequest()
                    .body("key is required");
        }

        User user = getUser(authentication);

        profileService.saveAnswer(
                user,
                request.key(),
                request.answer()
        );

        return ResponseEntity.ok(
                Map.of(
                        "message",
                        "Application detail saved successfully.",
                        "key",
                        request.key()
                )
        );
    }

    private User getUser(
            Authentication authentication
    ) {
        if (authentication == null
                || authentication.getName() == null) {
            throw new RuntimeException(
                    "User authentication is missing"
            );
        }

        return userRepository
                .findByEmail(authentication.getName())
                .orElseThrow(
                        () -> new RuntimeException(
                                "User not found"
                        )
                );
    }

    public record AnswerRequest(
            String key,
            String answer
    ) {}
}
