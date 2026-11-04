package com.jobscraper.job_platform.application.automation;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Playwright;

public record ApplicationBrowser(
        Playwright playwright,
        Browser browser,
        BrowserContext context
) {
}