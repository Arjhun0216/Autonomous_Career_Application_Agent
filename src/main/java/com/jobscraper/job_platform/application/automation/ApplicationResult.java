package com.jobscraper.job_platform.application.automation;

public record ApplicationResult(
        boolean success,
        String status,
        String message,
        String submittedUrl
) {

    public static ApplicationResult success(
            String message,
            String submittedUrl) {

        return new ApplicationResult(
                true,
                "APPLIED",
                message,
                submittedUrl
        );
    }

    public static ApplicationResult stageComplete(
            String message,
            String currentUrl) {

        return new ApplicationResult(
                true,
                "APPLICATION_PAGE_READY",
                message,
                currentUrl
        );
    }

    public static ApplicationResult failed(
            String message) {

        return new ApplicationResult(
                false,
                "FAILED",
                message,
                null
        );
    }

    public static ApplicationResult blocked(
            String message) {

        return new ApplicationResult(
                false,
                "MANUAL_ACTION_REQUIRED",
                message,
                null
        );
    }

    public static ApplicationResult authenticationRequired(
            String message,
            String jobUrl) {

        return new ApplicationResult(
                false,
                "FAILED",
                message,
                jobUrl
        );
    }
}
