package com.jobscraper.job_platform.application.automation;

import com.jobscraper.job_platform.application.JobApplication;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Resolves company-specific workflow knowledge without hard-coding browser
 * actions in the application agent.
 */
@Component
@RequiredArgsConstructor
public class CompanyWorkflowRegistry {

    private final List<CompanyWorkflow> workflows;

    public CompanyWorkflow resolve(Page page, JobApplication application) {

        for (CompanyWorkflow workflow : workflows) {
            try {
                if (workflow.supports(page, application)) {
                    return workflow;
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }
}
