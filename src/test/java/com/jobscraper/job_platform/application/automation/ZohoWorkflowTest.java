package com.jobscraper.job_platform.application.automation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

class ZohoWorkflowTest {

    private final ZohoApplicationAdapter adapter = new ZohoApplicationAdapter();
    private final ZohoWorkflow workflow = new ZohoWorkflow(adapter);

    @Test
    void selectsZohoWorkflowForZohoCareersUrl() {
        Page page = page("https://careers.zohocorp.com/jobs/123", "Careers");

        assertTrue(workflow.supports(page));
    }

    @Test
    void selectsZohoWorkflowWhenZohoIsIdentifiedByPageTitle() {
        Page page = page("https://jobs.example.test/opening/123", "Zoho Corporation - Careers");

        assertTrue(workflow.supports(page));
    }

    @Test
    void doesNotSelectZohoWorkflowForOtherCareerPages() {
        Page page = page("https://careers.example.test/jobs/123", "Example Careers");

        assertFalse(workflow.supports(page));
    }

    @Test
    void recognizesExplicitCookieAcceptActions() {
        assertTrue(ZohoApplicationAdapter.isCookieAcceptLabel("Accept all cookies"));
        assertTrue(ZohoApplicationAdapter.isCookieAcceptLabel("I agree"));
        assertFalse(ZohoApplicationAdapter.isCookieAcceptLabel("Manage preferences"));
        assertFalse(ZohoApplicationAdapter.isCookieAcceptLabel("Reject all"));
    }

    private Page page(String url, String title) {
        return (Page) Proxy.newProxyInstance(
                Page.class.getClassLoader(),
                new Class<?>[]{Page.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "url" -> url;
                    case "title" -> title;
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
