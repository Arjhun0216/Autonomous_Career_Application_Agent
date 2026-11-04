package com.jobscraper.job_platform.job;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobRepository jobRepository;

    @GetMapping("/{jobId}")
    public ResponseEntity<?> getJob(
            @PathVariable String jobId,
            Authentication authentication
    ) {

        if (authentication == null
                || !authentication.isAuthenticated()) {

            return ResponseEntity
                    .status(401)
                    .body(
                            Map.of(
                                    "message",
                                    "User is not authenticated"
                            )
                    );
        }

        Job job =
                jobRepository.findById(jobId)
                        .orElse(null);

        if (job == null) {
            return ResponseEntity
                    .notFound()
                    .build();
        }

        return ResponseEntity.ok(
                new JobDetailsResponse(
                        job.getJobId(),
                        job.getCompany(),
                        job.getTitle(),
                        job.getJd(),
                        job.getSkills(),
                        job.getExperienceLevel(),
                        job.getLocation(),
                        job.getSalary(),
                        job.getJobUrl(),
                        job.getSource(),
                        job.getPostedDate()
                )
        );
    }

    public record JobDetailsResponse(
            String jobId,
            String company,
            String title,
            String jd,
            String skills,
            String experienceLevel,
            String location,
            String salary,
            String jobUrl,
            String source,
            java.time.LocalDate postedDate
    ) {}
}
