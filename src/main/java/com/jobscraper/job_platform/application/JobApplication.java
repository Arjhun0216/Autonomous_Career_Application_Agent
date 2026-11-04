package com.jobscraper.job_platform.application;

import jakarta.persistence.*;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "job_applications",
    uniqueConstraints = {
        @UniqueConstraint(
            name = "unique_user_job",
            columnNames = {"user_id", "job_id"}
        )
    }
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "application_id")
    private Long applicationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "job_id", nullable = false)
    private String jobId;

    @Column(name = "company_name")
    private String companyName;

    @Column(name = "job_title")
    private String jobTitle;

    @Column(name = "job_url", length = 1000)
    private String jobUrl;

    @Column(name = "resume_path", length = 1000)
    private String resumePath;

    @Column(name = "cover_letter_path", length = 1000)
    private String coverLetterPath;

    @Column(name = "cover_letter_body", columnDefinition = "LONGTEXT")
    private String coverLetterBody;

    @Column(name = "status", nullable = false)
    @Builder.Default
    private String status = "CREATED";

    @Column(name = "cold_mail_subject", length = 500)
    private String coldMailSubject;

    @Column(
        name = "cold_mail_body",
        columnDefinition = "LONGTEXT"
)
private String coldMailBody;

    @Column(name = "hr_mail_id")
    private String hrMailId;

    @Column(name = "ready_at")
    private LocalDateTime readyAt;

    @Column(
        name = "error_message",
        columnDefinition = "LONGTEXT"
)
private String errorMessage;    

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;
}
