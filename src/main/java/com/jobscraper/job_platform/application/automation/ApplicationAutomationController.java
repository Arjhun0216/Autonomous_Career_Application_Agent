package com.jobscraper.job_platform.application.automation;

import com.jobscraper.job_platform.application.JobApplication;
import com.jobscraper.job_platform.application.JobApplicationRepository;
import com.jobscraper.job_platform.user.User;
import com.jobscraper.job_platform.user.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/application-agent")
@RequiredArgsConstructor
public class ApplicationAutomationController {

    private final ApplicationAgentService applicationAgentService;
    private final JobApplicationRepository applicationRepository;
    private final UserRepository userRepository;

    @PostMapping("/test/{applicationId}")
    public ResponseEntity<?> testApplication(
            @PathVariable Long applicationId) {

        try {

            JobApplication application =
                    applicationRepository
                            .findById(applicationId)
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "Application not found: "
                                                    + applicationId
                                    )
                            );

            User user =
                    userRepository
                            .findById(application.getUserId())
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "User not found: "
                                                    + application.getUserId()
                                    )
                            );

            ApplicationResult result =
                    applicationAgentService
                            .inspectApplicationPage(
                                    user,
                                    application
                            );

            return ResponseEntity.ok(result);

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .internalServerError()
                    .body(
                            ApplicationResult.failed(
                                    e.getMessage() == null
                                            ? "Application Agent failed"
                                            : e.getMessage()
                            )
                    );
        }
    }
}