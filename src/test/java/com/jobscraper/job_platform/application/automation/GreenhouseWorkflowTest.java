package com.jobscraper.job_platform.application.automation;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GreenhouseWorkflowTest {
    private final GreenhouseWorkflow workflow = new GreenhouseWorkflow();

    @Test
    void recognizesGreenhouseJobBoardsAndHostedApplicationUrls() {
        assertTrue(workflow.supports(page("https://job-boards.greenhouse.io/instead/jobs/7761474003")));
        assertTrue(workflow.supports(page("https://boards.greenhouse.com/acme/jobs/123")));
    }

    @Test
    void doesNotClaimLeverOrUnrelatedPages() {
        assertFalse(workflow.supports(page("https://jobs.lever.co/acme/123")));
        assertFalse(workflow.supports(page("https://careers.example.test/jobs/123")));
    }

    private Page page(String url) {
        return (Page) Proxy.newProxyInstance(Page.class.getClassLoader(), new Class<?>[]{Page.class},
                (proxy, method, args) -> method.getName().equals("url") ? url : null);
    }
}
