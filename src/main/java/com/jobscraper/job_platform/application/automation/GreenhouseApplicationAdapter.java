package com.jobscraper.job_platform.application.automation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Opens Greenhouse's application form; form filling remains in the guarded form agent. */
@Component
public class GreenhouseApplicationAdapter {
    public boolean supports(Page page) {
        if (page == null) return false;
        String url = page.url().toLowerCase(Locale.ROOT);
        return url.contains("greenhouse.io/") || url.contains("greenhouse.com/");
    }

    public ApplicationNavigationResult openApplicationForm(Page page) {
        if (!supports(page)) return ApplicationNavigationResult.failed("The page is not a Greenhouse job page.");
        try {
            if (applicationFormIsOpen(page)) {
                return ApplicationNavigationResult.success("Greenhouse application form is already open.", page);
            }

            acceptVisibleCookiePrompt(page);

            Locator actions = page.locator("a:visible, button:visible, [role='button']:visible, input[type='submit']:visible");
            Locator apply = null;
            for (int i = 0; i < actions.count(); i++) {
                Locator candidate = actions.nth(i);
                String text = safe(candidate.innerText()).trim().toLowerCase(Locale.ROOT);
                String aria = safe(candidate.getAttribute("aria-label")).trim().toLowerCase(Locale.ROOT);
                String value = safe(candidate.getAttribute("value")).trim().toLowerCase(Locale.ROOT);
                if (isApplyLabel(text) || isApplyLabel(aria) || isApplyLabel(value)) {
                    apply = candidate;
                    break;
                }
            }
            if (apply != null) {
                apply.scrollIntoViewIfNeeded();
                apply.click();
            } else {
                // Greenhouse job pages commonly render Apply as an anchor to the
                // embedded form farther down the same page. Scroll that form into view.
                Locator formAnchor = page.locator("#application, [id='application'], form#application-form");
                if (formAnchor.count() > 0) {
                    formAnchor.first().scrollIntoViewIfNeeded();
                } else {
                    return ApplicationNavigationResult.failed("Greenhouse Apply control and application form were not found on the opened job page.");
                }
            }

            page.waitForTimeout(3_000L);
            if (!applicationFormIsOpen(page)) {
                Locator formAnchor = page.locator("#application, [id='application'], form#application-form");
                if (formAnchor.count() > 0) formAnchor.first().scrollIntoViewIfNeeded();
            }
            if (!applicationFormIsOpen(page)) {
                return ApplicationNavigationResult.failed("Greenhouse Apply was clicked, but the application form did not appear. The browser stayed on the job page.");
            }
            return ApplicationNavigationResult.success("Greenhouse application form opened and verified.", page);
        } catch (Exception e) {
            return ApplicationNavigationResult.failed("Unable to open Greenhouse application form: " + safe(e.getMessage()));
        }
    }

    private boolean applicationFormIsOpen(Page page) {
        try {
            String body = page.locator("body").innerText().toLowerCase(Locale.ROOT);
            boolean hasNameFields = body.contains("first name") && body.contains("last name");
            boolean hasResumeUpload = page.locator("input[type='file']").count() > 0
                    || body.contains("resume/cv") || body.contains("upload your resume");
            return hasNameFields && hasResumeUpload;
        } catch (Exception ignored) {
            return false;
        }
    }

    private void acceptVisibleCookiePrompt(Page page) {
        Locator actions = page.locator("button:visible, [role='button']:visible, a:visible");
        for (int i = 0; i < actions.count(); i++) {
            Locator candidate = actions.nth(i);
            String label = (safe(candidate.innerText()) + " " + safe(candidate.getAttribute("aria-label")))
                    .trim().toLowerCase(Locale.ROOT);
            if (label.contains("accept all cookies") || label.contains("accept cookies")
                    || label.equals("accept all") || label.equals("allow all cookies")) {
                candidate.click();
                page.waitForTimeout(500L);
                return;
            }
        }
    }

    private boolean isApplyLabel(String label) {
        String normalized = label.replaceAll("\\s+", " ").trim();
        return normalized.equals("apply") || normalized.equals("apply for this job")
                || normalized.equals("apply now") || normalized.equals("apply to this job");
    }

    private String safe(String value) { return value == null ? "" : value; }
}
