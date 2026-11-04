package com.jobscraper.job_platform.application.automation;

import com.jobscraper.job_platform.application.JobApplication;
import com.microsoft.playwright.Page;
import org.springframework.stereotype.Component;

import java.util.Locale;

/** Keka careers form instructions and safe human-verification handoff. */
@Component
public class KekaWorkflow implements CompanyWorkflow {
    @Override public String getCompany() { return "Keka"; }

    @Override
    public boolean supports(Page page) {
        try { return page != null && page.url().toLowerCase(Locale.ROOT).contains("hr.keka.com/careers/"); }
        catch (Exception ignored) { return false; }
    }

    @Override
    public boolean supports(Page page, JobApplication application) {
        if (supports(page)) return true;
        return application != null && application.getCompanyName() != null
                && application.getCompanyName().toLowerCase(Locale.ROOT).contains("keka");
    }

    @Override
    public String getInstructions() {
        return """
                KEKA CAREERS APPLICATION
                ------------------------
                Start on the supplied Keka careers URL. Wait 3 seconds, accept any visible cookie consent,
                click the actual visible Apply control, and wait 3 seconds for the application form.
                If the supplied URL already shows the application form and has no Apply control, continue there.

                Upload the generated resume as the first form action. The Playwright executor waits
                5 seconds after the upload, then inspect the updated form before filling it.
                
                Use only the candidate database profile and saved answers:
                - First name = first_name; last name = last_name; email = email.
                - Mobile country-code dropdown = country_code. Search/type the saved code and select the
                  matching visible option; mobile number = phone in the separate phone input.
                - Experience = user_experience as stored; do not convert or invent experience.
                - Current salary: use an explicit current-salary/current-CTC value in other_details_json or
                  a saved answer for this application if one exists. If absent, save the exact question for
                  Pending Approvals.
                - Expected salary, available days to join, preferred location, and last working day are not
                  profile facts. Reuse a saved answer for this application if present; otherwise save each
                  required question for Pending Approvals. Include the live options for preferred location.
                - Current location = location from the database.
                - Check the privacy agreement only if it is an ordinary privacy/data-processing consent.
                  Do not mark a legal declaration or eligibility assertion without explicit user data.

                Continue through the form so all unknown required questions are collected. Do not submit
                while any required question is pending. After the user answers in Pending Approvals, reapply
                using those saved answers. Only report success when the page visibly confirms submission.

                HUMAN VERIFICATION
                ------------------
                If CAPTCHA or other human verification appears, stop immediately with CAPTCHA_BLOCKED and
                explain that the user must complete it manually in Chromium. Do not request, transcribe,
                infer, or submit a CAPTCHA answer, and do not claim the application was submitted.
                """;
    }
}
