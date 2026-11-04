package com.jobscraper.job_platform.application.automation;

import com.jobscraper.job_platform.application.JobApplication;
import com.microsoft.playwright.Dialog;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/** Opens the employer's Apply entry point and returns control at its login page. */
@Component
public class EmployerLoginHandoffAdapter {

    public ApplicationNavigationResult openApplyAndDetectLogin(Page page, JobApplication application) {
        String company = safe(application == null ? null : application.getCompanyName());
        if (page == null) return ApplicationNavigationResult.failed(company + " job page is unavailable.");

        try {
            page.onDialog(Dialog::accept);
            boolean workday = isWorkdayEmployer(company);
            if (workday) {
                dismissCookies(page);
            }

            Locator apply = findApply(page);
            if (apply == null && workday && company.toLowerCase(Locale.ROOT).contains("nvidia")) {
                Locator positions = findAction(page, "open positions", "find your next job");
                if (positions != null) {
                    positions.scrollIntoViewIfNeeded();
                    positions.click();
                    page.waitForTimeout(3000L);
                    page = latestPage(page);
                    dismissCookies(page);
                    apply = findApply(page);
                }
            }
            if (apply == null) {
                return ApplicationNavigationResult.failed(company + " Apply control was not found. Chromium is left open for review.");
            }

            apply.scrollIntoViewIfNeeded();
            apply.click();
            boolean accenture = company.toLowerCase(Locale.ROOT).contains("accenture");
            page.waitForTimeout(accenture ? 10_000L : 3000L);
            Page destination = latestPage(page);

            if (isLoginPage(destination, workday || accenture)) {
                return ApplicationNavigationResult.authenticationRequired(
                        company + " sign-in is required. Please authenticate manually in the open Chromium window; the application has not been submitted.");
            }
            return ApplicationNavigationResult.failed(
                    company + " Apply was clicked, but an authentication page was not detected. Chromium is left open for review.");
        } catch (Exception e) {
            return ApplicationNavigationResult.failed("Could not open " + company + " sign-in: " + safe(e.getMessage()));
        }
    }

    private boolean isWorkdayEmployer(String company) {
        String normalized = company.toLowerCase(Locale.ROOT);
        return normalized.contains("boeing") || normalized.contains("nvidia");
    }

    private void dismissCookies(Page page) {
        Locator accept = findAction(page, "accept all", "accept cookies", "allow all", "i agree");
        if (accept != null) {
            accept.click();
            page.waitForTimeout(500L);
        }
    }

    private Locator findApply(Page page) {
        return findAction(page, "apply", "apply now", "apply for this job", "apply to this job");
    }

    private Locator findAction(Page page, String... labels) {
        Locator actions = page.locator("a:visible, button:visible, [role='button']:visible, input[type='submit']:visible");
        for (int i = 0; i < actions.count(); i++) {
            Locator candidate = actions.nth(i);
            String text = (safe(candidate.innerText()) + " " + safe(candidate.getAttribute("aria-label")) + " "
                    + safe(candidate.getAttribute("value"))).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
            for (String label : labels) {
                if (text.equals(label) || text.startsWith(label + " ")) return candidate;
            }
        }
        return null;
    }

    private boolean isLoginPage(Page page, boolean workday) {
        try {
            String url = page.url().toLowerCase(Locale.ROOT);
            String body = page.locator("body").innerText().toLowerCase(Locale.ROOT);
            boolean urlSignalsLogin = url.contains("login") || url.contains("signin") || url.contains("sign-in")
                    || url.contains("auth") || url.contains("account");
            boolean formSignalsLogin = page.locator("input[type='password']:visible").count() > 0
                    && page.locator("input[type='email']:visible, input[autocomplete='username']:visible, input[name*='user' i]:visible").count() > 0;
            boolean copySignalsLogin = body.contains("sign in") || body.contains("log in") || body.contains("create account");
            return urlSignalsLogin || formSignalsLogin || (workday && copySignalsLogin);
        } catch (Exception ignored) {
            return false;
        }
    }

    private Page latestPage(Page fallback) {
        try {
            List<Page> pages = fallback.context().pages();
            return pages.isEmpty() ? fallback : pages.get(pages.size() - 1);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String safe(String value) { return value == null ? "" : value; }
}
