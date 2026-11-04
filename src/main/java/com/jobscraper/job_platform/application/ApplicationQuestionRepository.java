package com.jobscraper.job_platform.application;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ApplicationQuestionRepository
        extends JpaRepository<ApplicationQuestion, Long> {

    List<ApplicationQuestion> findByApplicationIdOrderByQuestionIdAsc(Long applicationId);

    List<ApplicationQuestion> findByApplicationIdAndStatusOrderByQuestionIdAsc(
            Long applicationId, String status);

    boolean existsByApplicationIdAndQuestionAndStatus(
            Long applicationId, String question, String status);

    boolean existsByApplicationIdAndStatus(Long applicationId, String status);
}
