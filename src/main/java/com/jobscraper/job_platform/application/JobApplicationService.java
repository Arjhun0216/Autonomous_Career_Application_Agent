package com.jobscraper.job_platform.application;

import lombok.RequiredArgsConstructor;

import com.jobscraper.job_platform.gmail.GmailSendService;
import com.jobscraper.job_platform.application.automation.ApplicationAgentService;
import com.jobscraper.job_platform.application.automation.ApplicationResult;
import com.jobscraper.job_platform.user.User;
import com.jobscraper.job_platform.user.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class JobApplicationService {

    private final UserRepository userRepository;

    private final GmailSendService gmailSendService;

    private final ApplicationAgentService applicationAgentService;

    private final JobApplicationRepository jobApplicationRepository;

    /**
     * Create a new application.
     *
     * This is called when the user clicks "Apply Now".
     *
     * The application is immediately stored with PROCESSING status.
     * Resume generation, cold mail generation and the actual
     * application automation will be connected later.
     */
    @Transactional
    public JobApplication createApplication(
            Long userId,
            String jobId,
            String companyName,
            String jobTitle,
            String jobUrl) {

        if (userId == null) {
            throw new RuntimeException("User ID is required");
        }

        if (jobId == null || jobId.isBlank()) {
            throw new RuntimeException("Job ID is required");
        }

        /*
         * Prevent the same user from applying to the same job twice.
         */
        if (jobApplicationRepository
                .existsByUserIdAndJobId(userId, jobId)) {

            throw new RuntimeException(
                    "You have already applied to this job"
            );
        }

        JobApplication application =
                JobApplication.builder()
                        .userId(userId)
                        .jobId(jobId)
                        .companyName(companyName)
                        .jobTitle(jobTitle)
                        .jobUrl(jobUrl)
                        .status("PROCESSING")
                        .build();

        return jobApplicationRepository.save(application);
    }

    /**
     * Get all applications belonging to a user.
     */
    public List<JobApplication> getUserApplications(Long userId) {

        if (userId == null) {
            throw new RuntimeException("User ID is required");
        }

        return jobApplicationRepository
                .findByUserIdOrderByCreatedAtDesc(userId);
    }

    /**
     * Get applications waiting for user approval.
     *
     * These will appear under:
     *
     * Hamburger Menu
     *       ↓
     * Pending Approvals
     */
    public List<JobApplication> getPendingApprovals(Long userId) {

        if (userId == null) {
            throw new RuntimeException("User ID is required");
        }

        return jobApplicationRepository
                .findByUserIdAndStatusOrderByCreatedAtDesc(
                        userId,
                        "READY_FOR_APPROVAL"
                );
    }

    /**
     * Get one application belonging to a user.
     */
    public JobApplication getApplication(
            Long userId,
            Long applicationId) {

        if (userId == null) {
            throw new RuntimeException("User ID is required");
        }

        if (applicationId == null) {
            throw new RuntimeException("Application ID is required");
        }

        JobApplication application =
                jobApplicationRepository
                        .findById(applicationId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Application not found"
                                )
                        );

        /*
         * Security check:
         *
         * A user must only be able to access
         * their own application.
         */
        if (!application.getUserId().equals(userId)) {
            throw new RuntimeException(
                    "You are not authorized to access this application"
            );
        }

        return application;
    }
/**
 * ============================================================
 * APPROVE AND SEND APPLICATION EMAIL
 * ============================================================
 */
@Transactional
    public ApprovalResult approveAndSendEmail(
        Long userId,
        Long applicationId) {

    if (userId == null) {
        throw new RuntimeException(
                "User ID is required"
        );
    }

    if (applicationId == null) {
        throw new RuntimeException(
                "Application ID is required"
        );
    }

    /*
     * Find application.
     */
    JobApplication application =
            jobApplicationRepository
                    .findById(applicationId)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "Application not found"
                            )
                    );

    /*
     * Security check.
     */
    if (!application
            .getUserId()
            .equals(userId)) {

        throw new RuntimeException(
                "You are not authorized to approve this application"
        );
    }

    /*
     * Only READY_FOR_APPROVAL can be approved.
     */
    if (!"READY_FOR_APPROVAL"
            .equals(application.getStatus())) {

        throw new RuntimeException(
                "Application is not waiting for approval"
        );
    }

    /*
     * Resume must exist.
     */
    if (application.getResumePath() == null ||
            application.getResumePath().isBlank()) {

        throw new RuntimeException(
                "Generated resume is missing"
        );
    }

    /*
     * Find user.
     */
    User user =
            userRepository
                    .findById(userId)
                    .orElseThrow(() ->
                            new RuntimeException(
                                    "User not found"
                            )
                    );

    String messageId = null;
    String emailError = null;

    if (application.getHrMailId() == null || application.getHrMailId().isBlank()) {
        emailError = "HR email is not available for this application.";
    } else if (application.getColdMailSubject() == null || application.getColdMailSubject().isBlank()) {
        emailError = "Generated email subject is missing.";
    } else if (application.getColdMailBody() == null || application.getColdMailBody().isBlank()) {
        emailError = "Generated email body is missing.";
    }

    if (emailError == null && user.isGmailConnected()) {
        application.setStatus("EMAIL_SENDING");
        application.setErrorMessage(null);
        jobApplicationRepository.save(application);

        try {
            messageId = gmailSendService.sendApplicationEmail(
                    user,
                    application.getHrMailId(),
                    application.getColdMailSubject(),
                    application.getColdMailBody(),
                    application.getResumePath()
            );
            application.setStatus("EMAIL_SENT");
            application.setErrorMessage(null);
            jobApplicationRepository.save(application);
        } catch (Exception e) {
            emailError = "Cold email was not sent: " + safeMessage(e);
            application.setStatus("EMAIL_FAILED");
            application.setErrorMessage(emailError);
            jobApplicationRepository.save(application);
        }
    } else if (emailError == null) {
        emailError = "Gmail is not connected. Reconnect Gmail to send the cold email.";
        application.setStatus("EMAIL_FAILED");
        application.setErrorMessage(emailError);
        jobApplicationRepository.save(application);
    }

    // Email delivery and career-site automation are independent. Always give
    // Playwright a chance to submit the application, even when Gmail's refresh
    // token is expired or revoked.
    ApplicationResult automationResult;
    try {
        automationResult = applicationAgentService.inspectApplicationPage(user, application);
    } catch (Exception automationException) {
        String message = "Unable to apply automatically: " + safeMessage(automationException);
        application.setStatus("FAILED");
        application.setErrorMessage(message);
        jobApplicationRepository.save(application);
        automationResult = ApplicationResult.failed(message);
    }

    return new ApprovalResult(
            messageId != null,
            messageId,
            emailError,
            application.getStatus(),
            automationResult.message()
    );
}

private String safeMessage(Exception exception) {
    return exception.getMessage() == null || exception.getMessage().isBlank()
            ? exception.getClass().getSimpleName()
            : exception.getMessage();
}

public record ApprovalResult(
        boolean emailSent,
        String messageId,
        String emailError,
        String applicationStatus,
        String applicationMessage
) {}
}
