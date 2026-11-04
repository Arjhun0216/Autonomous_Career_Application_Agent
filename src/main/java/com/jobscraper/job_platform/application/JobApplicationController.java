package com.jobscraper.job_platform.application;

import lombok.RequiredArgsConstructor;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.Authentication;

import org.springframework.web.bind.annotation.*;

import com.jobscraper.job_platform.user.User;
import com.jobscraper.job_platform.user.UserRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.List;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class JobApplicationController {


    private final UserRepository userRepository;

    private final JobApplicationRepository applicationRepository;

    private final JobApplicationService jobApplicationService;


    /**
 * ============================================================
 * APPROVE AND SEND EMAIL
 * ============================================================
 */
@PostMapping("/{applicationId}/approve-and-send")
public ResponseEntity<?> approveAndSend(
        @PathVariable Long applicationId,
        Authentication authentication) {

    try {

        String email =
                authentication.getName();

        User user =
                userRepository
                        .findByEmail(email)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User not found"
                                )
                        );

        JobApplicationService.ApprovalResult result =
                jobApplicationService
                        .approveAndSendEmail(
                                user.getUserId(),
                                applicationId
                        );

        return ResponseEntity.ok(result);

    } catch (RuntimeException e) {

        return ResponseEntity
                .badRequest()
                .body(
                        e.getMessage()
                );
    }
}


    /**
     * ============================================================
     * CREATE APPLICATION
     * ============================================================
     *
     * Called when the user clicks "Apply Now".
     */
    @PostMapping
    public ResponseEntity<?> createApplication(
            @RequestBody CreateApplicationRequest request,
            Authentication authentication) {

        try {

            String email =
                    authentication.getName();

            User user =
                    userRepository
                            .findByEmail(email)
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "User not found"
                                    )
                            );

            JobApplication application =
                    jobApplicationService.createApplication(
                            user.getUserId(),
                            request.jobId(),
                            request.companyName(),
                            request.jobTitle(),
                            request.jobUrl()
                    );

            return ResponseEntity.ok(application);

        } catch (RuntimeException e) {

            return ResponseEntity
                    .badRequest()
                    .body(e.getMessage());
        }
    }


    /**
     * ============================================================
     * GET ALL APPLICATIONS
     * ============================================================
     *
     * Returns all applications belonging to the
     * currently logged-in user.
     */
    @GetMapping
    public ResponseEntity<?> getMyApplications(
            Authentication authentication) {

        try {

            String email =
                    authentication.getName();

            User user =
                    userRepository
                            .findByEmail(email)
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "User not found"
                                    )
                            );

            List<JobApplication> applications =
                    jobApplicationService
                            .getUserApplications(
                                    user.getUserId()
                            );

            return ResponseEntity.ok(
                    applications
            );

        } catch (RuntimeException e) {

            return ResponseEntity
                    .badRequest()
                    .body(e.getMessage());
        }
    }


    /**
     * ============================================================
     * GET PENDING APPROVALS
     * ============================================================
     *
     * Returns applications which are ready
     * for user approval.
     */
    @GetMapping("/pending")
    public ResponseEntity<?> getPendingApprovals(
            Authentication authentication) {

        try {

            String email =
                    authentication.getName();

            User user =
                    userRepository
                            .findByEmail(email)
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "User not found"
                                    )
                            );

            List<JobApplication> applications =
                    jobApplicationService
                            .getPendingApprovals(
                                    user.getUserId()
                            );

            return ResponseEntity.ok(
                    applications
            );

        } catch (RuntimeException e) {

            return ResponseEntity
                    .badRequest()
                    .body(e.getMessage());
        }
    }


    /**
     * ============================================================
     * GET SPECIFIC APPLICATION
     * ============================================================
     */
    @GetMapping("/{applicationId}")
    public ResponseEntity<?> getApplication(
            @PathVariable Long applicationId,
            Authentication authentication) {

        try {

            String email =
                    authentication.getName();

            User user =
                    userRepository
                            .findByEmail(email)
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "User not found"
                                    )
                            );

            JobApplication application =
                    jobApplicationService.getApplication(
                            user.getUserId(),
                            applicationId
                    );

            return ResponseEntity.ok(
                    application
            );

        } catch (RuntimeException e) {

            return ResponseEntity
                    .badRequest()
                    .body(e.getMessage());
        }
    }


    /**
     * ============================================================
     * VIEW GENERATED RESUME
     * ============================================================
     *
     * Opens the generated PDF resume in the browser.
     *
     * Endpoint:
     *
     * GET /api/applications/{applicationId}/resume
     *
     * The application is first verified to belong
     * to the currently logged-in user.
     */
    @GetMapping(
            value = "/{applicationId}/resume",
            produces = MediaType.APPLICATION_PDF_VALUE
    )
    public ResponseEntity<Resource> viewResume(
            @PathVariable Long applicationId,
            Authentication authentication) {

        try {

            /*
             * ----------------------------------------------------
             * 1. Get currently logged-in user
             * ----------------------------------------------------
             */
            String email =
                    authentication.getName();

            User user =
                    userRepository
                            .findByEmail(email)
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "User not found"
                                    )
                            );


            /*
             * ----------------------------------------------------
             * 2. Find application
             * ----------------------------------------------------
             */
            JobApplication application =
                    applicationRepository
                            .findById(applicationId)
                            .orElseThrow(() ->
                                    new RuntimeException(
                                            "Application not found"
                                    )
                            );


            /*
             * ----------------------------------------------------
             * 3. SECURITY CHECK
             * ----------------------------------------------------
             *
             * Make sure this application belongs
             * to the logged-in user.
             */
            if (!application
                    .getUserId()
                    .equals(user.getUserId())) {

                return ResponseEntity
                        .status(403)
                        .build();
            }


            /*
             * ----------------------------------------------------
             * 4. Get resume path
             * ----------------------------------------------------
             */
            String resumePath =
                    application.getResumePath();


            if (resumePath == null ||
                    resumePath.isBlank()) {

                return ResponseEntity
                        .notFound()
                        .build();
            }


            /*
             * ----------------------------------------------------
             * 5. Convert path
             * ----------------------------------------------------
             */
            Path path =
                    Paths.get(resumePath);


            /*
             * ----------------------------------------------------
             * 6. Check whether PDF exists
             * ----------------------------------------------------
             */
            if (!Files.exists(path) ||
                    !Files.isRegularFile(path)) {

                return ResponseEntity
                        .notFound()
                        .build();
            }


            /*
             * ----------------------------------------------------
             * 7. Load PDF
             * ----------------------------------------------------
             */
            Resource resource =
                    new FileSystemResource(path);


            /*
             * ----------------------------------------------------
             * 8. Return PDF inline
             * ----------------------------------------------------
             *
             * "inline" tells the browser to open
             * the PDF instead of downloading it.
             */
            return ResponseEntity
                    .ok()
                    .contentType(
                            MediaType.APPLICATION_PDF
                    )
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" +
                                    path.getFileName() +
                                    "\""
                    )
                    .body(resource);

        } catch (RuntimeException e) {

            return ResponseEntity
                    .badRequest()
                    .build();

        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .internalServerError()
                    .build();
        }
    }


    /**
     * ============================================================
     * CREATE APPLICATION REQUEST
     * ============================================================
     */
    public record CreateApplicationRequest(
            String jobId,
            String companyName,
            String jobTitle,
            String jobUrl
    ) {
    }
}
