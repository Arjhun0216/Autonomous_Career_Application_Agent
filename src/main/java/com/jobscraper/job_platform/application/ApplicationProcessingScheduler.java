package com.jobscraper.job_platform.application;

import lombok.RequiredArgsConstructor;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ApplicationProcessingScheduler {

    private final JobApplicationRepository applicationRepository;
    private final ApplicationProcessingService processingService;

    /*
     * Process exactly ONE application per execution.
     *
     * fixedDelay starts counting after the previous execution
     * has completely returned.
     */
    @Scheduled(
            fixedDelay = 10000,
            initialDelay = 5000
    )
    public void processPendingApplications() {

        JobApplication application =
                applicationRepository
                        .findFirstByStatusOrderByCreatedAtAsc(
                                "PROCESSING"
                        );

        if (application == null) {
            return;
        }

        Long applicationId =
                application.getApplicationId();

        System.out.println(
                "QUEUE: attempting application "
                        + applicationId
        );

        boolean claimed =
                processingService.claimApplication(
                        applicationId
                );

        if (!claimed) {
            System.out.println(
                    "QUEUE: application "
                            + applicationId
                            + " was already claimed."
            );
            return;
        }

        JobApplication claimedApplication =
                applicationRepository
                        .findById(applicationId)
                        .orElse(null);

        if (claimedApplication == null) {
            System.err.println(
                    "QUEUE: application disappeared: "
                            + applicationId
            );
            return;
        }

        /*
         * IMPORTANT:
         *
         * No @Async.
         *
         * This call returns only after:
         * resume -> PDF -> cold mail -> DB save
         * has finished.
         */
        processingService.processApplication(
                claimedApplication
        );
    }
}
