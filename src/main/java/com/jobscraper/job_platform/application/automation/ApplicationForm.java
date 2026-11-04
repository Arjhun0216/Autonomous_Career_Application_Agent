package com.jobscraper.job_platform.application.automation;

import java.util.List;

public record ApplicationForm(
        List<FormField> fields
) {
}