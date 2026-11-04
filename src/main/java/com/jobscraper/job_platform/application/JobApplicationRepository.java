package com.jobscraper.job_platform.application;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobApplicationRepository
        extends JpaRepository<JobApplication, Long> {

    Optional<JobApplication> findByUserIdAndJobId(
            Long userId,
            String jobId
    );

    boolean existsByUserIdAndJobId(
            Long userId,
            String jobId
    );

    List<JobApplication> findByUserIdOrderByCreatedAtDesc(
            Long userId
    );

    List<JobApplication> findByUserIdAndStatusOrderByCreatedAtDesc(
            Long userId,
            String status
    );

    List<JobApplication> findByStatusOrderByCreatedAtAsc(
            String status
    );

    JobApplication findFirstByStatusOrderByCreatedAtAsc(
            String status
    );

    /*
     * Atomically claims an application.
     *
     * PROCESSING -> GENERATING
     *
     * Returns:
     * 1 = successfully claimed
     * 0 = somebody else already claimed it
     */
    @Modifying
    @Query("""
            UPDATE JobApplication a
            SET a.status = 'GENERATING'
            WHERE a.applicationId = :applicationId
              AND a.status = 'PROCESSING'
            """)
    int claimApplication(
            @Param("applicationId") Long applicationId
    );
}