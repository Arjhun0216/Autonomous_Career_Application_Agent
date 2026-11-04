package com.jobscraper.job_platform.application.automation;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Opens NVIDIA's Workday application entry point and hands authentication to the user. */
@Component
public class NvidiaApplicationFlow {
    private static final long STEP_WAIT_MS = 3000L;

    public ApplicationNavigationResult openApplyAndCheckWorkdaySignIn(Page page, String jobTitle) {
        if (page == null) return ApplicationNavigationResult.failed("NVIDIA job page is unavailable.");
        try {
            page.onDialog(com.microsoft.playwright.Dialog::accept);
            Page applicationPage = page;
            if (isNvidiaCareersLandingPage(applicationPage)) {
                dismissCookieBanner(applicationPage);
                Locator positions = findVisibleAction(applicationPage, "open positions", "find your next job");
                if (positions == null) {
                    return ApplicationNavigationResult.failed("NVIDIA careers page is open, but its Open Positions link was not found. Chromium is left open for review.");
                }
                positions.scrollIntoViewIfNeeded();
                positions.click();
                applicationPage.waitForTimeout(STEP_WAIT_MS);
                applicationPage = latestPage(applicationPage);

                ApplicationNavigationResult jobNavigation = openSelectedJob(applicationPage, jobTitle);
                if (!jobNavigation.success()) return jobNavigation;
                applicationPage = jobNavigation.page();
                applicationPage.waitForTimeout(STEP_WAIT_MS);
            }

            Locator apply = findVisibleAction(applicationPage, "apply now", "apply for this job", "apply");
            if (apply == null) {
                return ApplicationNavigationResult.failed("NVIDIA Apply control was not found. Chromium is left open for review.");
            }
            apply.scrollIntoViewIfNeeded();
            apply.click();
            applicationPage.waitForTimeout(STEP_WAIT_MS);
            applicationPage = latestPage(applicationPage);

            if (isWorkdayPage(applicationPage)) {
                try { applicationPage.bringToFront(); } catch (Exception ignored) { }
                return signInRequired();
            }
            return ApplicationNavigationResult.failed("NVIDIA Apply was clicked, but the Workday sign-in page was not detected. Chromium is left open for review.");
        } catch (Exception e) {
            return ApplicationNavigationResult.failed("NVIDIA application setup could not continue: " + safe(e.getMessage()) + ". Chromium is left open for review.");
        }
    }

    private boolean isNvidiaCareersLandingPage(Page page) {
        try {
            String url = page.url().toLowerCase(Locale.ROOT);
            return url.contains("nvidia.com/en-us/about-nvidia/careers");
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isWorkdayPage(Page page) {
        try {
            String url = page.url().toLowerCase(Locale.ROOT);
            String body = page.locator("body").innerText().toLowerCase(Locale.ROOT);
            return url.contains("myworkdayjobs.com")
                    || url.contains("workday.com") && (body.contains("sign in") || body.contains("create account"));
        } catch (Exception ignored) { return false; }
    }

    private void dismissCookieBanner(Page page) {
        Locator accept = findVisibleAction(page, "accept all");
        if (accept != null) {
            accept.click();
            page.waitForTimeout(1000L);
        }
    }

    private ApplicationNavigationResult openSelectedJob(Page page, String jobTitle) {
        if (jobTitle == null || jobTitle.isBlank()) {
            return ApplicationNavigationResult.failed("NVIDIA careers opened, but the saved job title is empty, so the matching position cannot be selected.");
        }

        try {
            Locator search = page.locator("input[type='search']:visible, input[placeholder*='search' i]:visible, input[aria-label*='search' i]:visible");
            if (search.count() > 0) {
                search.first().fill(jobTitle);
                search.first().press("Enter");
                page.waitForTimeout(3000L);
            }

            Locator titleLink = page.getByRole(AriaRole.LINK,
                    new Page.GetByRoleOptions().setName(jobTitle).setExact(false));
            for (int i = 0; i < titleLink.count(); i++) {
                Locator candidate = titleLink.nth(i);
                if (candidate.isVisible()) {
                    candidate.click();
                    page.waitForTimeout(STEP_WAIT_MS);
                    return ApplicationNavigationResult.success("Opened the NVIDIA job matching the saved title.", latestPage(page));
                }
            }

            Locator titleText = page.getByText(jobTitle, new Page.GetByTextOptions().setExact(false));
            for (int i = 0; i < titleText.count(); i++) {
                Locator candidate = titleText.nth(i);
                if (candidate.isVisible()) {
                    candidate.click();
                    page.waitForTimeout(STEP_WAIT_MS);
                    return ApplicationNavigationResult.success("Opened the NVIDIA job matching the saved title.", latestPage(page));
                }
            }
            return ApplicationNavigationResult.failed("Could not find the NVIDIA position matching '" + jobTitle + "' after opening careers. Chromium is left open for review.");
        } catch (Exception e) {
            return ApplicationNavigationResult.failed("Could not open the matching NVIDIA position: " + safe(e.getMessage()) + ". Chromium is left open for review.");
        }
    }

    private Locator findAutofillAction(Page page) {
        for (com.microsoft.playwright.Frame frame : page.frames()) {
            Locator action = findAutofillAction(frame);
            if (action != null) return action;
        }
        return null;
    }

    private Locator findAutofillAction(com.microsoft.playwright.Frame frame) {
        com.microsoft.playwright.Frame.GetByRoleOptions exactName = new com.microsoft.playwright.Frame.GetByRoleOptions()
                .setName("Autofill with Resume")
                .setExact(true);
        Locator buttons = frame.getByRole(AriaRole.BUTTON, exactName);
        for (int i = 0; i < buttons.count(); i++) {
            Locator candidate = buttons.nth(i);
            if (candidate.isVisible()) return candidate;
        }

        Locator links = frame.getByRole(AriaRole.LINK, new com.microsoft.playwright.Frame.GetByRoleOptions()
                .setName("Autofill with Resume")
                .setExact(true));
        for (int i = 0; i < links.count(); i++) {
            Locator candidate = links.nth(i);
            if (candidate.isVisible()) return candidate;
        }

        Locator matches = frame.getByText(
                "Autofill with Resume",
                new com.microsoft.playwright.Frame.GetByTextOptions().setExact(true)
        );
        for (int i = 0; i < matches.count(); i++) {
            Locator candidate = matches.nth(i);
            if (candidate.isVisible()) return candidate;
        }
        return null;
    }

    private String safeUrl(Page page) {
        try { return page.url(); } catch (Exception ignored) { return "unknown page"; }
    }

    private Locator findVisibleAction(Page page, String... labels) {
        Locator actions = page.locator("a:visible, button:visible, [role='button']:visible, input[type='submit']:visible");
        for (int i = 0; i < actions.count(); i++) {
            Locator candidate = actions.nth(i);
            String label = (safe(candidate.innerText()) + " " + safe(candidate.getAttribute("aria-label")) + " "
                    + safe(candidate.getAttribute("value"))).toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
            for (String expected : labels) {
                if (label.equals(expected)) return candidate;
            }
        }
        return null;
    }

    private Page latestPage(Page fallback) {
        try {
            var pages = fallback.context().pages();
            return pages.isEmpty() ? fallback : pages.get(pages.size() - 1);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private Page findGoogleSignInPage(Page fallback) {
        try {
            for (Page candidate : fallback.context().pages()) {
                if (hasGoogleSignIn(candidate)) return candidate;
            }
        } catch (Exception ignored) { }
        return hasGoogleSignIn(fallback) ? fallback : null;
    }

    private boolean hasGoogleSignIn(Page page) {
        try {
            String url = page.url().toLowerCase(Locale.ROOT);
            String body = page.locator("body").innerText().toLowerCase(Locale.ROOT);
            return url.contains("accounts.google.com")
                    || (body.contains("sign in with google") || body.contains("continue with google")
                    || body.contains("choose an account"))
                    && (body.contains("google") || url.contains("google"));
        } catch (Exception ignored) {
            return false;
        }
    }

    private ApplicationNavigationResult signInRequired() {
        return ApplicationNavigationResult.authenticationRequired(
                "NVIDIA Workday authentication is required. Please sign in manually in the open Chromium window, then continue the application.");
    }

    private String safe(String value) { return value == null ? "" : value; }
}
