package com.jobscraper.job_platform.skill;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class SkillVerificationController {

    private final LowMatchSkillService skillService;
    private final SkillLearningService learningService;

    @GetMapping("/missing")
    public ResponseEntity<?> getMissingSkills(
            Authentication authentication
    ) {

        try {

            String email =
                    requireEmail(authentication);

            return ResponseEntity.ok(
                    skillService.findMissingSkills(
                            email
                    )
            );

        } catch (Exception e) {

            return error(e);
        }
    }

    /*
     * Job-specific verification.
     *
     * The frontend calls this when the job match is below 50%.
     */
    @GetMapping("/verify")
    public ResponseEntity<?> verifyJobSkills(
            @RequestParam String jobId,
            Authentication authentication
    ) {

        try {

            String email =
                    requireEmail(authentication);

            return ResponseEntity.ok(
                    skillService.verifyJobSkills(
                            email,
                            jobId
                    )
            );

        } catch (Exception e) {

            return error(e);
        }
    }

    @PostMapping("/confirm")
    public ResponseEntity<?> confirmSkill(
            @RequestBody SkillConfirmationRequest request,
            Authentication authentication
    ) {

        try {

            String email =
                    requireEmail(authentication);

            if (request == null
                    || request.skill() == null
                    || request.skill().isBlank()) {

                return ResponseEntity
                        .badRequest()
                        .body(
                                Map.of(
                                        "message",
                                        "Skill is required"
                                )
                        );
            }

            return ResponseEntity.ok(
                    skillService.confirmSkill(
                            email,
                            request.skill()
                    )
            );

        } catch (Exception e) {

            return error(e);
        }
    }

    /*
     * Generate the three-level learning path:
     *
     * 1. What is it?
     * 2. How does it work?
     * 3. How to use it with the candidate stack?
     */
    @PostMapping("/learning-resources")
    public ResponseEntity<?> learningResources(
            @RequestBody LearningRequest request,
            Authentication authentication
    ) {

        try {

            String email =
                    requireEmail(authentication);

            if (request == null
                    || request.skill() == null
                    || request.skill().isBlank()) {

                return ResponseEntity
                        .badRequest()
                        .body(
                                Map.of(
                                        "message",
                                        "Skill is required"
                                )
                        );
            }

            return ResponseEntity.ok(
                    learningService.generateResources(
                            email,
                            request.skill(),
                            request.experience()
                    )
            );

        } catch (Exception e) {

            return error(e);
        }
    }

    private String requireEmail(
            Authentication authentication
    ) {

        if (authentication == null
                || !authentication.isAuthenticated()) {

            throw new RuntimeException(
                    "User is not authenticated"
            );
        }

        return authentication.getName();
    }

    private ResponseEntity<?> error(
            Exception e
    ) {

        e.printStackTrace();

        String message =
                e.getMessage() == null
                        ? "Unable to process request"
                        : e.getMessage();

        return ResponseEntity
                .internalServerError()
                .body(
                        Map.of(
                                "message",
                                message
                        )
                );
    }

    public record SkillConfirmationRequest(
            String skill
    ) {}

    public record LearningRequest(
            String skill,
            String experience
    ) {}
}
