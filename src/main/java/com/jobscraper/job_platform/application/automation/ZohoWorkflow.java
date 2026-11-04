package com.jobscraper.job_platform.application.automation;

import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Zoho Careers workflow knowledge for Gemini.
 *
 * IMPORTANT: This class does not click buttons or fill fields.
 * It tells Gemini what the Zoho application flow is expected to be.
 * Gemini observes the live page and chooses the next action.
 */
@Component
@RequiredArgsConstructor
public class ZohoWorkflow implements CompanyWorkflow {

    private final ZohoApplicationAdapter zohoApplicationAdapter;

    @Override
    public String getCompany() {
        return "Zoho";
    }

    @Override
    public boolean supports(Page page) {
        // Keep workflow selection consistent with the Zoho adapter. The
        // adapter recognizes Zoho job pages by URL and title, while the old
        // check here only matched careers.zohocorp.com URLs. That mismatch
        // could send a recognized Zoho job page through the generic flow.
        return zohoApplicationAdapter.supports(page);
    }

    @Override
    public String getInstructions() {
        return """
                ZOHO CAREERS APPLICATION WORKFLOW
                =================================

                Start from the supplied Zoho Careers job URL.

                The current browser page is the source of truth. Inspect the
                live page and decide what the next application action should be.
                Do not assume a fixed button name, URL, selector, page layout,
                modal, form structure, or navigation sequence.

                GENERAL FLOW
                ------------
                - Inspect the current job/application page.
                - If ANY cookie, privacy, or consent request/banner is present, accept it
                  using the actual visible accept/agree control shown in the live page details
                  before continuing. Never guess the button name or selector.
                - If something blocks interaction, determine from the live page
                  what must be resolved first.
                - This project deliberately does NOT use Zoho's Easy Apply / candidate
                  sign-in control (the control labelled "Easy Apply" that opens the
                  Zoho candidate login flow). The Java ZohoApplicationAdapter uses
                  the visible job-page "I'm Interested" entry point to open the
                  application form. Continue from that form.
                - After every action, inspect the newly rendered page.
                - Continue through the application to final review. If unresolved required
                  questions have been recorded, stop BEFORE final submission and return them
                  for Pending Approval. Never click final Submit while questions remain pending.
                  If all required questions have trusted answers, continue to submit normally.
                  Only a verified visible success message counts as completion.

                FORM HANDLING
                -------------
                - Read the current DOM and visible page content.
                - Decide which field/control should be used next.
                - Use only information available in the supplied candidate context.
                - RESUME FIRST: upload the supplied generated resume as soon as the actual
                  application form exposes its resume upload control. Do not use Easy Apply.
                - After upload, wait for resume parsing/extraction to finish, up to ten minutes maximum.
                  Poll for visible processing/completion/error states; do not sleep blindly for ten minutes.
                  After extraction, compare the visible fields with the supplied verified user profile.
                  Preserve correct extracted values; correct only missing or incorrect values using trusted data.
                - Split the candidate name at the final word: all preceding words are first name and the final word is last name. If the name has one word, do not invent a surname.
                - Use the explicitly saved Male/Female profile value. Never infer gender from the name. Use Mr for Male and Ms for Female when the form asks for a title.
                - Use education[].endYear only for Year of Graduation. Use email, phone, and current location from the database. Use the saved country_code and enter phone digits separately.
                - Map experience 0 to Fresher, 2 to 2-3 years, and 3 to 3-4 years only when those options are available. Do not invent a band for other values.
                - For "How did you hear about us?" choose the visible "Zoho Careers Page" option when available.
                - Skills may be taken from the verified skills/resume profile. Current employer, current CTC, expected CTC, notice period, and preferred location are not available from the database in this workflow: save each required question for Pending Approval and do not guess. For preferred location, use only an actual visible option.
                - Use the unified FILL action for text values and native dropdowns. For a native select,
                  inspect the actual options and select only an existing matching option. For a custom
                  dropdown, open it, inspect the live options, and select the matching option in a later action.
                  Never fill a field based only on a guessed selector or an ambiguous field ID.
                - Never guess sensitive, legal, authorization, sponsorship,
                  citizenship, military, demographic, security, or CAPTCHA data.
                - If work authorization itself is not explicitly answered in the profile,
                  save the exact question for Pending Approval. Work authorization country
                  defaults to India, but this is NOT an answer to eligibility/authorization.
                - For unknown mandatory non-sensitive text fields, use PENDING_USER_INPUT
                  temporarily, record the exact question, and continue through the form.
                  These temporary values must never be submitted.
                - If the application asks how the candidate heard about the
                  company, use the exact visible "Zoho Careers Page" option when
                  available; otherwise use a semantically equivalent visible option
                  only when clearly supported, or save the question for Pending Approval.

                ERROR RECOVERY
                --------------
                If an action does not produce the expected result, do not repeat
                the same action blindly. Inspect the current page again and make
                a new decision from the new state.

                COMPLETION
                ----------
                Continue the loop until the CURRENT PAGE visibly confirms that
                the application was successfully submitted. Do not claim success
                based only on an assumed navigation or click.
                """;
    }
}
