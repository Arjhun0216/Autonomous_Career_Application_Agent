package com.jobscraper.job_platform.application.automation;

import com.jobscraper.job_platform.application.JobApplication;
import com.microsoft.playwright.Page;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;

/** Employer handoff workflows that stop when the employer requests sign-in. */
@Component
public class ManualLoginWorkflow implements CompanyWorkflow {
    private static final Set<String> EMPLOYERS = Set.of(
            "hcl", "hcltech", "hcl technologies", "accenture", "amazon", "boeing", "nvidia"
    );

    @Override public String getCompany() { return "Manual employer sign-in"; }

    @Override public boolean supports(Page page) { return false; }

    @Override
    public boolean supports(Page page, JobApplication application) {
        if (application == null || application.getCompanyName() == null) return false;
        String company = application.getCompanyName().toLowerCase(Locale.ROOT).trim();
        return EMPLOYERS.contains(company)
                || company.contains("hcl")
                || company.contains("hcl technologies")
                || company.contains("accenture")
                || company.contains("amazon")
                || company.contains("boeing")
                || company.contains("nvidia");
    }

    @Override
    public String getInstructions() {
        return "Stop at employer authentication and ask the user to sign in manually. Never enter credentials or claim submission.";
    }
}
