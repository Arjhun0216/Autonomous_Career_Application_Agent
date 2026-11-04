package com.jobscraper.job_platform.application.automation;

import com.microsoft.playwright.Page;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class LeverWorkflow implements CompanyWorkflow {
    @Override public String getCompany() { return "Lever"; }

    @Override
    public boolean supports(Page page) {
        return page != null && page.url().toLowerCase(Locale.ROOT).contains("jobs.lever.co/");
    }

    @Override
    public String getInstructions() {
        return """
                LEVER APPLICATION
                Wait for the job page to settle, find its visible Apply control, click it, and wait for the Lever form to appear.
                Fill candidate details from the database, upload the generated resume and cover letter when those fields are present,
                reuse saved answers, and pause for Pending Approvals whenever a required answer is unknown. Never invent answers or submit with pending required questions.
                """;
    }
}
