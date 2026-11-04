package com.jobscraper.job_platform.application.automation;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.LoadState;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * NVIDIA / Workday specific browser adapter.
 *
 * This class DOES NOT decide what the candidate should answer.
 *
 * Gemini remains responsible for deciding:
 *
 *      - what to click
 *      - what to fill
 *      - what option to select
 *      - when resume should be uploaded
 *      - when the application is ready
 *      - when submission is successful
 *
 * This adapter only handles browser mechanics that are specific
 * to NVIDIA's Workday career pages.
 */
@Component
public class NvidiaApplicationAdapter {

    private static final String NVIDIA_HOST =
            "nvidia.wd5.myworkdayjobs.com";

    private static final int PAGE_WAIT_MS = 1500;

    /**
     * Check whether the supplied URL belongs to NVIDIA Workday.
     */
    public boolean supports(String url) {

        if (url == null || url.isBlank()) {
            return false;
        }

        return url
                .toLowerCase(Locale.ROOT)
                .contains(NVIDIA_HOST);
    }

    /**
     * Check the currently loaded browser page.
     */
    public boolean supports(Page page) {

        if (page == null) {
            return false;
        }

        try {
            return supports(page.url());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Wait until the NVIDIA page has actually rendered.
     *
     * Workday pages are highly dynamic, therefore we don't immediately
     * hand the page to Gemini after navigation.
     */
    public void waitUntilReady(Page page) {

        if (page == null) {
            return;
        }

        try {

            page.waitForLoadState(
                    LoadState.DOMCONTENTLOADED,
                    new Page.WaitForLoadStateOptions()
                            .setTimeout(30000)
            );

        } catch (Exception e) {

            System.out.println(
                    "NVIDIA DOMContentLoaded wait warning: "
                            + e.getMessage()
            );
        }

        try {
            page.waitForTimeout(PAGE_WAIT_MS);
        } catch (Exception ignored) {
        }

        try {

            page.waitForLoadState(
                    LoadState.LOAD,
                    new Page.WaitForLoadStateOptions()
                            .setTimeout(15000)
            );

        } catch (Exception e) {

            System.out.println(
                    "NVIDIA load-state wait warning: "
                            + e.getMessage()
            );
        }

        try {
            page.waitForTimeout(PAGE_WAIT_MS);
        } catch (Exception ignored) {
        }
    }

    /**
     * Bring the NVIDIA application page to the foreground.
     */
    public void focus(Page page) {

        if (page == null) {
            return;
        }

        try {
            page.bringToFront();
        } catch (Exception e) {
            System.out.println(
                    "Unable to bring NVIDIA page to front: "
                            + e.getMessage()
            );
        }
    }

    /**
     * Return additional instructions for Gemini.
     *
     * Gemini gets these instructions along with the actual page state.
     */
    public String getLlmInstructions(Page page) {

        return """
                NVIDIA / WORKDAY APPLICATION RULES

                You are operating an NVIDIA Workday application.

                IMPORTANT:

                1. The browser page supplied to you is the CURRENT page.
                2. Perform exactly ONE browser action.
                3. After Java executes your action, the page will be inspected again.
                4. Do not assume that the next page has already loaded.
                5. Do not invent fields or values.
                6. Use visible labels and actual DOM field identifiers.
                7. If an application page asks for a resume/CV and an upload control
                   exists, choose UPLOAD.
                8. If a page has "Fill with Resume", "Autofill with Resume",
                   "Upload Resume", "Attach Resume", or a file input,
                   use the available resume upload mechanism.
                9. For normal NVIDIA application-account login:
                   - email = candidate email
                   - password = password@123
                10. If the page offers "Continue with Google", "Sign in with Google",
                    or another Google authentication option, click that option.
                11. Do NOT enter a Google password.
                12. Do NOT enter OTP, MFA, security codes, CAPTCHA answers,
                    or other human-verification values.
                13. If Google authentication or human verification requires the
                    candidate to intervene, return AUTHENTICATION_REQUIRED.
                14. Never claim that an application was submitted unless the
                    current browser page visibly confirms it.
                15. Never submit until all required fields on the current page
                    have been handled.
                16. If a question is sensitive, legally significant, or cannot be
                    safely inferred from the candidate profile, return
                    USER_INPUT_REQUIRED.
                17. Never click Back, Cancel, Delete, Clear, or Save Draft.

                CURRENT PAGE URL:
                %s

                CURRENT PAGE TITLE:
                %s
                """.formatted(
                safeUrl(page),
                safeTitle(page)
        );
    }

    private String safeUrl(Page page) {

        try {
            return page.url();
        } catch (Exception e) {
            return "";
        }
    }

    private String safeTitle(Page page) {

        try {
            return page.title();
        } catch (Exception e) {
            return "";
        }
    }
}