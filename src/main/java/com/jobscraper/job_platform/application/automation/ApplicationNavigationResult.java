package com.jobscraper.job_platform.application.automation;

import com.microsoft.playwright.Page;

public record ApplicationNavigationResult(
        boolean success,
        boolean blocked,
        boolean authenticationRequired,
        String message,
        Page page
) {

    public static ApplicationNavigationResult success(
            String message,
            Page page
    ) {

        return new ApplicationNavigationResult(
                true,
                false,
                false,
                message,
                page
        );
    }

    public static ApplicationNavigationResult failed(
            String message
    ) {

        return new ApplicationNavigationResult(
                false,
                false,
                false,
                message,
                null
        );
    }

    public static ApplicationNavigationResult blocked(
            String message
    ) {

        return new ApplicationNavigationResult(
                false,
                true,
                false,
                message,
                null
        );
    }

    public static ApplicationNavigationResult authenticationRequired(
            String message
    ) {

        return new ApplicationNavigationResult(
                false,
                false,
                true,
                message,
                null
        );
    }
}