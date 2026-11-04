package com.jobscraper.job_platform.application.automation;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;

import org.springframework.stereotype.Service;

@Service
public class ApplicationBrowserService {

    /**
     * Slow enough that we can visually watch the automation.
     *
     * 1000 ms = 1 second between Playwright actions.
     */
    private static final double SLOW_MO_MS = 1000;

    private static final double DEFAULT_TIMEOUT_MS = 30000;

    private static final double NAVIGATION_TIMEOUT_MS = 30000;

    /**
     * Creates a visible browser.
     */
    public ApplicationBrowser createBrowser() {

        System.out.println(
                "======================================"
        );

        System.out.println(
                "STARTING APPLICATION BROWSER"
        );

        System.out.println(
                "Slow motion: "
                        + SLOW_MO_MS
                        + " ms"
        );

        System.out.println(
                "======================================"
        );

        Playwright playwright =
                Playwright.create();

        Browser browser =
                playwright.chromium().launch(
                        new BrowserType.LaunchOptions()
                                .setHeadless(false)
                                .setSlowMo(SLOW_MO_MS)
                                .setArgs(java.util.List.of("--start-maximized"))
                );

        BrowserContext context =
                browser.newContext(
                        new Browser.NewContextOptions()
                                .setAcceptDownloads(true)
                );

        context.setDefaultTimeout(
                DEFAULT_TIMEOUT_MS
        );

        context.setDefaultNavigationTimeout(
                NAVIGATION_TIMEOUT_MS
        );

        return new ApplicationBrowser(
                playwright,
                browser,
                context
        );
    }

    /**
     * Close browser safely.
     */
    public void closeBrowser(
            ApplicationBrowser applicationBrowser
    ) {

        if (applicationBrowser == null) {
            return;
        }

        try {

            if (applicationBrowser.context() != null) {
                applicationBrowser
                        .context()
                        .close();
            }

        } catch (Exception e) {

            System.out.println(
                    "Browser context close warning: "
                            + e.getMessage()
            );
        }

        try {

            if (applicationBrowser.browser() != null) {
                applicationBrowser
                        .browser()
                        .close();
            }

        } catch (Exception e) {

            System.out.println(
                    "Browser close warning: "
                            + e.getMessage()
            );
        }

        try {

            if (applicationBrowser.playwright() != null) {
                applicationBrowser
                        .playwright()
                        .close();
            }

        } catch (Exception e) {

            System.out.println(
                    "Playwright close warning: "
                            + e.getMessage()
            );
        }
    }
}