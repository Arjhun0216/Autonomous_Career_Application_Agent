package com.jobscraper.job_platform.application.automation;

import com.microsoft.playwright.Page;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class IBMWorkflow implements CompanyWorkflow {
    @Override public String getCompany() { return "IBM"; }

    @Override
    public boolean supports(Page page) {
        if (page == null) return false;
        String url = page.url().toLowerCase(Locale.ROOT);
        return url.contains("ibm.com/careers")
                || url.contains("careers.ibm.com")
                || url.contains("ibmglobal.avature.net")
                || url.contains("ibm.wd1.myworkdayjobs.com")
                || url.contains("ibm.wd5.myworkdayjobs.com");
    }

    @Override
    public String getInstructions() {
        return """
                IBM CAREERS LOGIN HANDOFF
                - This workflow stops at IBM login. Do not enter credentials or attempt to bypass authentication.
                - The IBM adapter waits for the page, clicks the visible Apply Now control, and checks for IBMid sign-in.
                - When IBM sign-in appears, report AUTHENTICATION_REQUIRED and ask the user to complete login manually in the open Chromium window.
                - Do not claim the job application was submitted.
                """;
    }
}
