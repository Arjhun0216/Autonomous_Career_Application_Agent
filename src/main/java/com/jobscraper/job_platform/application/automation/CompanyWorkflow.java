package com.jobscraper.job_platform.application.automation;

import com.microsoft.playwright.Page;
import com.jobscraper.job_platform.application.JobApplication;

/**
 * Company-specific knowledge for the LLM application agent.
 *
 * Implementations describe the expected website/application workflow.
 * They must not hard-code the browser actions themselves.
 */
public interface CompanyWorkflow {

    String getCompany();

    boolean supports(Page page);

    default boolean supports(Page page, JobApplication application) {
        return supports(page);
    }

    String getInstructions();
}
