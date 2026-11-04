package com.jobscraper.job_platform.application.automation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Opens IBM's sign-in handoff and leaves authentication to the user. */
@Component
public class IBMApplicationAdapter {
    public ApplicationNavigationResult clickApplyAndCheckLogin(Page page) {
        if (page == null) return ApplicationNavigationResult.failed("IBM job page is unavailable.");
        try {
            Locator actions = page.locator("a:visible, button:visible, [role='button']:visible, input[type='submit']:visible");
            Locator apply = null;
            for (int index = 0; index < actions.count(); index++) {
                Locator candidate = actions.nth(index);
                String label = (safe(candidate.innerText()) + " "
                        + safe(candidate.getAttribute("aria-label")) + " "
                        + safe(candidate.getAttribute("value")))
                        .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
                if (label.equals("apply now") || label.contains("apply now ")
                        || label.equals("apply for this job") || label.equals("apply")) {
                    apply = candidate;
                    break;
                }
            }
            if (apply == null) {
                return ApplicationNavigationResult.failed("IBM Apply Now control was not found on the job page.");
            }

            apply.scrollIntoViewIfNeeded();
            apply.click();
            page.waitForTimeout(2500L);

            if (isLoginPage(page)) {
                return ApplicationNavigationResult.authenticationRequired(
                        "IBM sign-in is required. Please log in manually in the open Chromium window, then continue the application there.");
            }
            return ApplicationNavigationResult.failed(
                    "IBM Apply Now was clicked, but a login page was not detected. The browser is left open for manual review.");
        } catch (Exception e) {
            return ApplicationNavigationResult.failed("Could not open IBM sign-in: " + safe(e.getMessage()));
        }
    }

    private boolean isLoginPage(Page page) {
        try {
            String url = page.url().toLowerCase(Locale.ROOT);
            String body = page.locator("body").innerText().toLowerCase(Locale.ROOT);
            boolean loginUrl = url.contains("login") || url.contains("signin")
                    || url.contains("sign-in") || url.contains("auth.ibm.com");
            boolean ibmIdCopy = body.contains("ibmid") || body.contains("sign in to ibm")
                    || body.contains("sign in with your ibm") || body.contains("log in to ibm");
            boolean passwordForm = page.locator("input[type='password']:visible").count() > 0
                    && page.locator("input[type='email']:visible, input[name*='user' i]:visible, input[autocomplete='username']:visible").count() > 0;
            return loginUrl || ibmIdCopy || passwordForm;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String safe(String value) { return value == null ? "" : value; }
}
