package com.jobscraper.job_platform.application.automation;

public record FormField(
        String tag,
        String type,
        String name,
        String id,
        String placeholder,
        String label,
        boolean required
) {
}