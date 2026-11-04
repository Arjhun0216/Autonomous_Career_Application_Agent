package com.jobscraper.job_platform.application;

import com.jobscraper.job_platform.application.automation.ApplicationAgentService;
import com.jobscraper.job_platform.user.User;
import com.jobscraper.job_platform.user.UserRepository;
import com.jobscraper.job_platform.application.automation.ApplicationResult;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationReapplyController {

    private final JobApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final ApplicationAgentService applicationAgentService;
    private final ApplicationQuestionRepository questionRepository;

    /**
     * Re-run the SAME application.
     *
     * No new application record is created.
     */
    @PostMapping("/{applicationId}/reapply")
    public ResponseEntity<?> reapply(
            @PathVariable Long applicationId,
            Authentication authentication
    ) {

        try {

            if (authentication == null
                    || authentication.getName() == null) {

                return ResponseEntity
                        .status(401)
                        .body(
                                "Authentication required."
                        );
            }

            User user =
                    userRepository
                            .findByEmail(
                                    authentication.getName()
                            )
                            .orElseThrow(
                                    () -> new RuntimeException(
                                            "User not found."
                                    )
                            );

            JobApplication application =
                    applicationRepository
                            .findById(
                                    applicationId
                            )
                            .orElseThrow(
                                    () -> new RuntimeException(
                                            "Application not found."
                                    )
                            );

            /*
             * SECURITY:
             * Application must belong to current user.
             */

            if (!application
                    .getUserId()
                    .equals(
                            user.getUserId()
                    )) {

                return ResponseEntity
                        .status(403)
                        .body(
                                "You are not allowed to reapply for this application."
                        );
            }

            /* Retry after user answers or if the earlier attempt stopped
             * before browser automation because Gmail delivery failed. */
            String status = application.getStatus();
            boolean waitingForAnswers = "USER_INPUT_REQUIRED".equalsIgnoreCase(status);
            boolean emailFailedBeforeAutomation = "EMAIL_FAILED".equalsIgnoreCase(status);
            if (!waitingForAnswers && !emailFailedBeforeAutomation) {

                return ResponseEntity
                        .badRequest()
                        .body(
                                "This application is not eligible for retry."
                        );
            }

            if (waitingForAnswers
                    && questionRepository.existsByApplicationIdAndStatus(applicationId, "PENDING")) {
                return ResponseEntity.badRequest().body(
                        "Please answer all required application questions before reapplying."
                );
            }

            /*
             * Re-run SAME application.
             */

            ApplicationResult result =
                    applicationAgentService.inspectApplicationPage(
                            user,
                            application
                    );

            return ResponseEntity.ok(
                    result
            );

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .badRequest()
                    .body(
                            e.getMessage()
                    );
        }
    }
}
