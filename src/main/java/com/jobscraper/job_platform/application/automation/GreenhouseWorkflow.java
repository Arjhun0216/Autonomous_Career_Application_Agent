package com.jobscraper.job_platform.application.automation;

import com.microsoft.playwright.Page;
import org.springframework.stereotype.Component;

import java.util.Locale;

@Component
public class GreenhouseWorkflow implements CompanyWorkflow {
    @Override public String getCompany() { return "Greenhouse"; }

    @Override
    public boolean supports(Page page) {
        if (page == null) return false;
        String url = page.url().toLowerCase(Locale.ROOT);
        return url.contains("greenhouse.io/") || url.contains("greenhouse.com/");
    }

    @Override
    public String getInstructions() {
        return """
                GREENHOUSE APPLICATION
                - The adapter tries to open and verify the application form. If the job page is still showing an Apply control, click it and inspect the resulting form.
                - Accept a visible cookie prompt before continuing. Do not remain idle on the job description page.
                - Fill First Name, Last Name, Email, country code, and phone from the candidate database fields exactly.
                - For a country-code autocomplete: type the saved calling code, wait one second, then choose the exact matching visible suggestion.
                - For school/university autocomplete, type Anna, wait two seconds, and select the exact visible "Anna University" suggestion.
                - For degree/qualification, choose B.Tech when that option is available.
                - Fill CGPA/GPA from the matching CGPA key in education data. Do not substitute graduation year or experience.
                - Fill ordinary rank fields with 0 as requested, but do NOT apply that default to Department Rank.
                - Department Rank is not present in the profile: save it as a required Pending Approval question and pause before submitting.
                - Use the LinkedIn URL from the LinkedIn entry in other_links; do not substitute another social profile.
                - Upload the generated resume to the Resume/CV field. If separate Resume and CV file fields are present, upload the same generated resume to both; if combined, upload it once.
                - Upload the generated cover letter to the separate Cover Letter field when it is present and optional or required.
                - Answer only from explicit profile data or a saved answer in other_details. Do not guess custom questions (for example, computer type).
                - Save each unanswered required question for Pending Approvals and stop before submitting.
                - Submit only after all required questions are answered and saved; then wait for the page's visible submission confirmation.
                - This flow must not use Dropbox or Google Drive controls; upload the generated local files directly.
                """;
    }
}
