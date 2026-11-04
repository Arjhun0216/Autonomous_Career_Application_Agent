package com.jobscraper.job_platform.application.automation;

import com.microsoft.playwright.Page;
import org.springframework.stereotype.Component;

@Component
public class GoogleApplicationAdapter {

    public boolean supports(Page page) {
        if (page == null) return false;
        String url = page.url();
        String title = safeTitle(page);
        return url.contains("google.com")
                || url.contains("googleusercontent.com")
                || title.toLowerCase().contains("google careers");
    }

    public String getLlmInstructions(Page page) {
        return """
                GOOGLE CAREERS ADAPTER
                ======================
                You are operating a Google Careers application.

                - Use only information present in the candidate profile or previously answered application questions.
                - Never invent work authorization, sponsorship, military, demographic, legal, education, or employment facts.
                - If a mandatory question is unknown but the browser can continue, return UNKNOWN_REQUIRED + TEMPORARY_INPUT.
                - For free-text temporary values use PENDING_USER_INPUT.
                - For select/radio fields choose only an option visibly offered by the page.
                - Continue through the form and discover all remaining required unknown questions.
                - Never submit while Java has recorded any PENDING application question.
                - If Google authentication, MFA, OTP, or human verification is required, stop with the appropriate status.
                - A successful application requires visible confirmation from the Google application page.
                """;
    }

    private String safeTitle(Page page) {
        try {
            return page.title();
        } catch (Exception e) {
            return "";
        }
    }
}
