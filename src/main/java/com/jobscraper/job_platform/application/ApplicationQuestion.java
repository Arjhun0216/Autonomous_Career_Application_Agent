package com.jobscraper.job_platform.application;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "application_questions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApplicationQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "question_id")
    private Long questionId;

    @Column(name = "application_id", nullable = false)
    private Long applicationId;

    @Column(name = "question", columnDefinition = "LONGTEXT", nullable = false)
    private String question;

    @Column(name = "field_id", length = 500)
    private String fieldId;

    @Column(name = "question_type", length = 50)
    private String questionType;

    @Column(name = "temporary_answer", columnDefinition = "LONGTEXT")
    private String temporaryAnswer;

    @Column(name = "final_answer", columnDefinition = "LONGTEXT")
    private String finalAnswer;

    @Column(name = "available_options", columnDefinition = "LONGTEXT")
    private String availableOptions;

    @Column(name = "status", length = 30, nullable = false)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "required")
    @Builder.Default
    private boolean required = true;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;
}
