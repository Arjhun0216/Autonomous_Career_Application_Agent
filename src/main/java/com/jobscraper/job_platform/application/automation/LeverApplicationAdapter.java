package com.jobscraper.job_platform.application.automation;

import com.jobscraper.job_platform.resume.GeminiClient;
import com.jobscraper.job_platform.user.User;

import com.microsoft.playwright.FileChooser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;


/**
 * ================================================================
 * LLM CONTROLLED APPLICATION AGENT
 * ================================================================
 *
 * IMPORTANT:
 *
 * This class does NOT contain application-form-specific knowledge.
 *
 * Java is responsible only for:
 *
 *      1. Observing the browser page.
 *      2. Sending page state to Gemini.
 *      3. Executing Gemini's ONE requested action.
 *      4. Waiting for the page to settle.
 *      5. Observing the page again.
 *
 * Gemini is responsible for deciding:
 *
 *      - what field means what
 *      - what value should be entered
 *      - which button should be clicked
 *      - which dropdown option should be selected
 *      - whether a resume should be uploaded
 *      - whether the page changed
 *      - whether authentication is required
 *      - whether CAPTCHA/MFA/OTP requires the user
 *      - whether the application is complete
 *
 * NO FORM FIELD NAMES ARE HARDCODED.
 *
 * ================================================================
 */
@Service
@RequiredArgsConstructor
public class LeverApplicationAdapter {

    /*
     * Maximum number of LLM/browser iterations.
     *
     * This prevents an accidental infinite loop.
     */
    private static final int MAX_STEPS = 80;


    /*
     * Wait after browser actions.
     *
     * Gemini decides the action.
     * Java waits for the page to settle before observing again.
     */
    private static final int ACTION_WAIT_MS = 2500;


    /*
     * Longer wait for page navigation / authentication / uploads.
     */
    private static final int LONG_WAIT_MS = 5000;


    /*
     * Maximum page text sent to Gemini.
     */
    private static final int MAX_PAGE_TEXT = 50_000;


    /*
     * Maximum Gemini response size.
     */
    private static final int GEMINI_MAX_TOKENS = 5000;


    private final GeminiClient geminiClient;

    private final ObjectMapper objectMapper;


    /*
     * ================================================================
     * MAIN AGENT
     * ================================================================
     */

    public Result run(
            Page page,
            User user,
            String resumePath
    ) {

        if (page == null) {
            return Result.failed(
                    "Application page is missing."
            );
        }


        if (user == null) {
            return Result.failed(
                    "User profile is missing."
            );
        }


        if (
                resumePath == null
                        || resumePath.isBlank()
        ) {

            return Result.failed(
                    "Generated resume path is missing."
            );
        }


        Path resume =
                Path.of(resumePath);


        if (!Files.exists(resume)) {

            return Result.failed(
                    "Generated resume does not exist: "
                            + resumePath
            );
        }


        try {

            page.setDefaultTimeout(
                    30_000
            );


            System.out.println(
                    "=============================================="
            );

            System.out.println(
                    "LLM CONTROLLED APPLICATION AGENT STARTED"
            );

            System.out.println(
                    "=============================================="
            );


            /*
             * --------------------------------------------------------
             * Initial observation
             * --------------------------------------------------------
             */

            waitForPageToSettle(
                    page,
                    LONG_WAIT_MS
            );


            /*
             * --------------------------------------------------------
             * Build candidate profile once.
             *
             * Gemini receives this on every iteration.
             * --------------------------------------------------------
             */

            String candidateProfile =
                    buildCandidateProfile(
                            user
                    );


            /*
             * --------------------------------------------------------
             * Agent loop
             * --------------------------------------------------------
             */

            for (
                    int step = 1;
                    step <= MAX_STEPS;
                    step++
            ) {

                System.out.println(
                        "=============================================="
                );

                System.out.println(
                        "APPLICATION AGENT STEP "
                                + step
                                + "/"
                                + MAX_STEPS
                );

                System.out.println(
                        "URL: "
                                + safeUrl(page)
                );

                System.out.println(
                        "=============================================="
                );


                /*
                 * ----------------------------------------------------
                 * Human verification detection
                 *
                 * This is only a safety stop.
                 *
                 * We DO NOT attempt to solve CAPTCHA.
                 * ----------------------------------------------------
                 */

                if (
                        hasHumanVerification(
                                page
                        )
                ) {

                    return Result.blocked(
                            "CAPTCHA/human verification detected. Manual intervention required."
                    );
                }


                /*
                 * ----------------------------------------------------
                 * Capture current page.
                 *
                 * No assumptions about Lever form structure.
                 * ----------------------------------------------------
                 */

                PageSnapshot snapshot =
                        capturePage(
                                page
                        );


                /*
                 * ----------------------------------------------------
                 * Send current page + candidate profile + job context
                 * to Gemini.
                 * ----------------------------------------------------
                 */

                String prompt =
                        buildAgentPrompt(
                                snapshot,
                                candidateProfile,
                                resumePath,
                                step
                        );


                System.out.println(
                        "Sending current page state to Gemini..."
                );


                String raw =
                        geminiClient.generate(
                                prompt,
                                GEMINI_MAX_TOKENS
                        );


                Action action =
                        parseAction(
                                raw
                        );


                System.out.println(
                        "GEMINI ACTION: "
                                + action.type()
                );


                if (
                        action.reason() != null
                                && !action.reason().isBlank()
                ) {

                    System.out.println(
                            "GEMINI REASON: "
                                    + action.reason()
                    );
                }


                /*
                 * ----------------------------------------------------
                 * Handle terminal states
                 * ----------------------------------------------------
                 */

                if (
                        action.type()
                                == ActionType.DONE
                ) {

                    /*
                     * Gemini says application is complete.
                     *
                     * We still verify the page before marking success.
                     */

                    if (
                            isSubmissionSuccessful(
                                    page
                            )
                    ) {

                        return Result.success(
                                "Application submitted successfully.",
                                safeUrl(page)
                        );
                    }


                    /*
                     * Do not blindly trust DONE.
                     *
                     * Give Gemini another observation instead.
                     */

                    System.out.println(
                            "Gemini returned DONE but submission confirmation was not detected."
                    );


                    page.waitForTimeout(
                            ACTION_WAIT_MS
                    );


                    continue;
                }


                if (
                        action.type()
                                == ActionType.STOP
                ) {

                    return Result.failed(
                            nonBlank(
                                    action.reason(),
                                    "Gemini stopped the application agent."
                            )
                    );
                }


                if (
        action.type()
                == ActionType.ASK_USER
) {

    return Result.userInputRequired(
            nonBlank(
                    action.reason(),
                    "User input is required."
            )
    );
}


                if (
                        action.type()
                                == ActionType.AUTHENTICATION_REQUIRED
                ) {

                    return Result.authenticationRequired(
                            nonBlank(
                                    action.reason(),
                                    "Authentication is required."
                            )
                    );
                }


                if (
                        action.type()
                                == ActionType.BLOCKED
                ) {

                    return Result.blocked(
                            nonBlank(
                                    action.reason(),
                                    "Application is blocked."
                            )
                    );
                }


                /*
                 * ----------------------------------------------------
                 * Execute exactly ONE Gemini action.
                 * ----------------------------------------------------
                 */

                ActionResult execution =
                        executeAction(
                                page,
                                action,
                                resume
                        );


                if (
                        execution.status()
                                == ExecutionStatus.FAILED
                ) {

                    System.out.println(
                            "ACTION EXECUTION FAILED: "
                                    + execution.message()
                    );


                    /*
                     * Do not immediately fail.
                     *
                     * Observe the browser again and let Gemini
                     * recover from the changed state.
                     */

                    page.waitForTimeout(
                            ACTION_WAIT_MS
                    );


                    continue;
                }


                if (
                        execution.status()
                                == ExecutionStatus.SUBMITTED
                ) {

                    page.waitForTimeout(
                            LONG_WAIT_MS
                    );


                    if (
                            isSubmissionSuccessful(
                                    page
                            )
                    ) {

                        return Result.success(
                                "Application submitted successfully.",
                                safeUrl(page)
                        );
                    }


                    /*
                     * Submission click happened but no confirmation.
                     *
                     * Observe again instead of clicking repeatedly.
                     */

                    System.out.println(
                            "Submission action executed, but confirmation is not yet visible."
                    );


                    continue;
                }


                /*
                 * ----------------------------------------------------
                 * Normal action.
                 *
                 * Wait, then next loop captures the new page.
                 * ----------------------------------------------------
                 */

                waitForPageToSettle(
                        page,
                        execution.longWait()
                                ? LONG_WAIT_MS
                                : ACTION_WAIT_MS
                );


                /*
                 * Check CAPTCHA after every action.
                 */

                if (
                        hasHumanVerification(
                                page
                        )
                ) {

                    return Result.blocked(
                            "CAPTCHA/human verification detected after browser action."
                    );
                }
            }


            return Result.failed(
                    "Application agent reached the maximum number of steps."
            );


        } catch (Exception e) {

            e.printStackTrace();


            return Result.failed(
                    "LLM application agent failed: "
                            + (
                            e.getMessage() == null
                                    ? "Unknown error"
                                    : e.getMessage()
                    )
            );
        }
    }


    /*
     * ================================================================
     * CAPTURE PAGE
     * ================================================================
     *
     * IMPORTANT:
     *
     * This method contains NO application-specific assumptions.
     *
     * It simply describes what currently exists in the browser.
     * ================================================================
     */

    private PageSnapshot capturePage(
            Page page
    ) {

        try {

            String url =
                    safeUrl(page);


            String title =
                    safeTitle(page);


            String bodyText =
                    safeBodyText(page);


            if (
                    bodyText.length()
                            > MAX_PAGE_TEXT
            ) {

                bodyText =
                        bodyText.substring(
                                0,
                                MAX_PAGE_TEXT
                        );
            }


            String controls =
                    inspectBrowserControls(
                            page
                    );


            return new PageSnapshot(
                    url,
                    title,
                    bodyText,
                    controls
            );


        } catch (Exception e) {

            return new PageSnapshot(
                    safeUrl(page),
                    safeTitle(page),
                    "",
                    "{}"
            );
        }
    }


    /*
     * ================================================================
     * INSPECT BROWSER CONTROLS
     * ================================================================
     *
     * We do not decide what fields mean.
     *
     * We simply expose their DOM properties to Gemini.
     * ================================================================
     */

    private String inspectBrowserControls(
            Page page
    ) {

        try {

            Object result =
                    page.evaluate(
                            """
                            () => {

                                const clean = value =>
                                    (value || '')
                                        .replace(/\\s+/g, ' ')
                                        .trim();


                                const visible = element => {

                                    const style =
                                        window.getComputedStyle(element);

                                    const rect =
                                        element.getBoundingClientRect();

                                    return style.display !== 'none'
                                        && style.visibility !== 'hidden'
                                        && rect.width > 0
                                        && rect.height > 0;
                                };


                                const attr = (
                                    element,
                                    name
                                ) =>
                                    element
                                        ? (
                                            element.getAttribute(name)
                                            || ''
                                        )
                                        : '';


                                const getLabel = element => {

                                    if (
                                        element.labels
                                        &&
                                        element.labels.length
                                    ) {

                                        return Array.from(
                                            element.labels
                                        )
                                        .map(
                                            label =>
                                                clean(
                                                    label.innerText
                                                    ||
                                                    label.textContent
                                                )
                                        )
                                        .filter(Boolean)
                                        .join(' ');
                                    }


                                    const labelledBy =
                                        attr(
                                            element,
                                            'aria-labelledby'
                                        );


                                    if (labelledBy) {

                                        const text =
                                            labelledBy
                                                .split(/\\s+/)
                                                .map(
                                                    id =>
                                                        document.getElementById(id)
                                                )
                                                .filter(Boolean)
                                                .map(
                                                    element =>
                                                        clean(
                                                            element.innerText
                                                            ||
                                                            element.textContent
                                                        )
                                                )
                                                .filter(Boolean)
                                                .join(' ');


                                        if (text) {
                                            return text;
                                        }
                                    }


                                    const ariaLabel =
                                        attr(
                                            element,
                                            'aria-label'
                                        );


                                    if (ariaLabel) {
                                        return ariaLabel;
                                    }


                                    const placeholder =
                                        attr(
                                            element,
                                            'placeholder'
                                        );


                                    if (placeholder) {
                                        return placeholder;
                                    }


                                    const parent =
                                        element.closest('label');


                                    if (parent) {

                                        return clean(
                                            parent.innerText
                                            ||
                                            parent.textContent
                                        );
                                    }


                                    /*
                                     * Give Gemini surrounding field
                                     * context as well.
                                     */

                                    const container =
                                        element.closest(
                                            '[data-qa], ' +
                                            '[data-testid], ' +
                                            '[class*="field"], ' +
                                            '[class*="form"], ' +
                                            '[class*="application"]'
                                        );


                                    if (container) {

                                        const text =
                                            clean(
                                                container.innerText
                                                ||
                                                container.textContent
                                            );


                                        if (text) {

                                            return text.substring(
                                                0,
                                                500
                                            );
                                        }
                                    }


                                    return '';
                                };


                                const fields = [];


                                const nodes =
                                    document.querySelectorAll(
                                        'input, textarea, select, ' +
                                        'button, ' +
                                        '[role="combobox"], ' +
                                        '[role="radio"], ' +
                                        '[role="checkbox"]'
                                    );


                                let fieldIndex = 0;


                                for (
                                    const element
                                    of nodes
                                ) {

                                    if (
                                        !visible(element)
                                    ) {
                                        continue;
                                    }


                                    const tag =
                                        element.tagName
                                            .toLowerCase();


                                    const type =
                                        (
                                            element.getAttribute(
                                                'type'
                                            )
                                            ||
                                            ''
                                        ).toLowerCase();


                                    /*
                                     * Hidden fields are not useful
                                     * to Gemini.
                                     */

                                    if (
                                        type === 'hidden'
                                    ) {
                                        continue;
                                    }


                                    const options = [];


                                    if (
                                        tag === 'select'
                                    ) {

                                        for (
                                            const option
                                            of element.options
                                        ) {

                                            options.push({
                                                text:
                                                    clean(
                                                        option.textContent
                                                    ),

                                                value:
                                                    option.value,

                                                selected:
                                                    !!option.selected
                                            });
                                        }
                                    }


                                    const fieldId =
                                        'browser-field-'
                                        +
                                        fieldIndex;


                                    /*
                                     * Store our temporary DOM
                                     * identifier so Gemini can refer
                                     * to this exact element.
                                     */

                                    element.setAttribute(
                                        'data-llm-field-id',
                                        fieldId
                                    );


                                    fields.push({

                                        fieldId:
                                            fieldId,

                                        tag:
                                            tag,

                                        type:
                                            type || tag,

                                        role:
                                            attr(
                                                element,
                                                'role'
                                            ),

                                        label:
                                            getLabel(
                                                element
                                            ),

                                        id:
                                            attr(
                                                element,
                                                'id'
                                            ),

                                        name:
                                            attr(
                                                element,
                                                'name'
                                            ),

                                        dataQa:
                                            attr(
                                                element,
                                                'data-qa'
                                            ),

                                        dataTestId:
                                            attr(
                                                element,
                                                'data-testid'
                                            ),

                                        placeholder:
                                            attr(
                                                element,
                                                'placeholder'
                                            ),

                                        required:
                                            !!element.required
                                            ||
                                            attr(
                                                element,
                                                'aria-required'
                                            ) === 'true',

                                        value:
                                            type === 'file'
                                                ? ''
                                                : (
                                                    element.value
                                                    || ''
                                                ),

                                        checked:
                                            !!element.checked,

                                        disabled:
                                            !!element.disabled,

                                        options:
                                            options
                                    });


                                    fieldIndex++;
                                }


                                const buttons = [];


                                const buttonNodes =
                                    document.querySelectorAll(
                                        'button, input[type="submit"], input[type="button"]'
                                    );


                                let buttonIndex = 0;


                                for (
                                    const button
                                    of buttonNodes
                                ) {

                                    if (
                                        !visible(button)
                                    ) {
                                        continue;
                                    }


                                    buttons.push({

                                        buttonId:
                                            'browser-button-'
                                            +
                                            buttonIndex,

                                        text:
                                            clean(
                                                button.innerText
                                                ||
                                                button.value
                                                ||
                                                ''
                                            ),

                                        type:
                                            attr(
                                                button,
                                                'type'
                                            ),

                                        ariaLabel:
                                            attr(
                                                button,
                                                'aria-label'
                                            ),

                                        id:
                                            attr(
                                                button,
                                                'id'
                                            ),

                                        name:
                                            attr(
                                                button,
                                                'name'
                                            )
                                    });


                                    buttonIndex++;
                                }


                                return JSON.stringify({

                                    url:
                                        location.href,

                                    title:
                                        document.title,

                                    fields:
                                        fields,

                                    buttons:
                                        buttons
                                });
                            }
                            """
                    );


            return result == null
                    ? "{}"
                    : String.valueOf(result);


        } catch (Exception e) {

            return "{}";
        }
    }


    /*
     * ================================================================
     * GEMINI PROMPT
     * ================================================================
     */

    private String buildAgentPrompt(
            PageSnapshot page,
            String candidateProfile,
            String resumePath,
            int step
    ) {

        return
                """
                You are the browser decision engine for an automated job
                application system.

                You control a real browser.

                The Java program will execute exactly ONE action returned
                by you.

                After that action, Java will capture the browser again and
                send the new state to you.

                Therefore:

                DO NOT plan multiple actions.

                Return EXACTLY ONE action.

                ========================================================
                CORE PRINCIPLE
                ========================================================

                Java is only the browser executor.

                YOU are responsible for understanding the page.

                Do NOT assume a particular ATS form structure.

                Do NOT assume field names.

                Do NOT assume the current page is a normal application form.

                Analyze the actual page content and controls supplied below.

                The page may be:

                - job page
                - application page
                - login page
                - registration page
                - resume upload page
                - questionnaire
                - screening questions
                - demographic questionnaire
                - authentication page
                - confirmation page
                - error page
                - another page in the application workflow

                ========================================================
                ONE ACTION RULE
                ========================================================

                Return only ONE action.

                Valid actions:

                FILL
                SELECT
                CHECK
                UNCHECK
                CLICK
                UPLOAD
                WAIT
                SUBMIT
                ASK_USER
                AUTHENTICATION_REQUIRED
                BLOCKED
                DONE
                STOP

                ========================================================
                FIELD SELECTION
                ========================================================

                Use the exact fieldId supplied by Java.

                Do not invent field IDs.

                Example:

                browser-field-4

                ========================================================
                CANDIDATE INFORMATION
                ========================================================

                Use ONLY information available in the candidate profile.

                Never fabricate:

                - education
                - GPA
                - experience
                - employment history
                - visa status
                - work authorization
                - sponsorship
                - legal information
                - demographic information
                - disability information
                - veteran status
                - security information
                - certifications
                - skills

                If required information is unavailable, use ASK_USER.

                ========================================================
                RESUME
                ========================================================

                Generated resume path:

                %s

                If the page contains a file upload control and the resume
                should be uploaded, return:

                {
                  "type": "UPLOAD",
                  "target": "browser-field-X",
                  "value": "%s",
                  "reason": "..."
                }

                Do not assume which field is the resume field.

                Identify it from the actual page.

                ========================================================
                AUTHENTICATION
                ========================================================

                If the current page is an ordinary employer/ATS login page
                and the candidate's stored credentials can be used,
                identify the relevant fields from the page.

                Do not attempt:

                - CAPTCHA
                - OTP
                - MFA
                - security challenges
                - Google password entry
                - Google OTP

                If Google authentication is presented, return:

                AUTHENTICATION_REQUIRED

                and explain that manual Google authentication is required.

                ========================================================
                CAPTCHA
                ========================================================

                Never attempt to solve or bypass CAPTCHA.

                Return:

                BLOCKED

                ========================================================
                SUBMISSION
                ========================================================

                Only return SUBMIT when the page actually appears ready
                for final submission.

                Do not submit merely because a button says "Submit".

                Analyze the surrounding page and current state.

                ========================================================
                WAIT
                ========================================================

                Use WAIT when:

                - page is still loading
                - upload is processing
                - navigation has not completed
                - an action triggered dynamic content
                - the page needs time before the next decision

                ========================================================
                IMPORTANT
                ========================================================

                Never return multiple actions.

                Never return a sequence.

                Never assume what comes next.

                Observe -> decide one action -> wait -> observe again.

                ========================================================
                CURRENT STEP
                ========================================================

                Step:
                %d

                ========================================================
                CANDIDATE PROFILE
                ========================================================

                %s

                ========================================================
                CURRENT PAGE URL
                ========================================================

                %s

                ========================================================
                CURRENT PAGE TITLE
                ========================================================

                %s

                ========================================================
                CURRENT PAGE TEXT
                ========================================================

                %s

                ========================================================
                CURRENT BROWSER CONTROLS
                ========================================================

                %s

                ========================================================
                RESPONSE FORMAT
                ========================================================

                Return JSON only.

                For FILL:

                {
                  "type": "FILL",
                  "target": "browser-field-X",
                  "value": "value",
                  "reason": "why this field should receive this value"
                }

                For SELECT:

                {
                  "type": "SELECT",
                  "target": "browser-field-X",
                  "value": "exact option",
                  "reason": "why"
                }

                For CHECK:

                {
                  "type": "CHECK",
                  "target": "browser-field-X",
                  "value": "true",
                  "reason": "why"
                }

                For UNCHECK:

                {
                  "type": "UNCHECK",
                  "target": "browser-field-X",
                  "value": "false",
                  "reason": "why"
                }

                For CLICK:

                {
                  "type": "CLICK",
                  "target": "browser-field-X",
                  "value": "",
                  "reason": "why"
                }

                For UPLOAD:

                {
                  "type": "UPLOAD",
                  "target": "browser-field-X",
                  "value": "%s",
                  "reason": "why"
                }

                For SUBMIT:

                {
                  "type": "SUBMIT",
                  "target": "browser-button-X",
                  "value": "",
                  "reason": "why"
                }

                For WAIT:

                {
                  "type": "WAIT",
                  "target": "",
                  "value": "3000",
                  "reason": "page needs time to settle"
                }

                For ASK_USER:

                {
                  "type": "ASK_USER",
                  "target": "",
                  "value": "",
                  "reason": "exact information required from user"
                }

                For AUTHENTICATION_REQUIRED:

                {
                  "type": "AUTHENTICATION_REQUIRED",
                  "target": "",
                  "value": "",
                  "reason": "manual authentication required"
                }

                For BLOCKED:

                {
                  "type": "BLOCKED",
                  "target": "",
                  "value": "",
                  "reason": "CAPTCHA or human verification detected"
                }

                For DONE:

                {
                  "type": "DONE",
                  "target": "",
                  "value": "",
                  "reason": "application has been submitted"
                }

                For STOP:

                {
                  "type": "STOP",
                  "target": "",
                  "value": "",
                  "reason": "cannot safely continue"
                }

                ========================================================
                END CURRENT PAGE
                ========================================================
                """.formatted(
                        resumePath,
                        resumePath,
                        step,
                        candidateProfile,
                        page.url(),
                        page.title(),
                        page.bodyText(),
                        page.controls(),
                        resumePath
                );
    }


    /*
     * ================================================================
     * EXECUTE ONE ACTION
     * ================================================================
     */

    private ActionResult executeAction(
            Page page,
            Action action,
            Path resume
    ) {

        try {

            switch (
                    action.type()
            ) {

                case FILL:

                    return executeFill(
                            page,
                            action
                    );


                case SELECT:

                    return executeSelect(
                            page,
                            action
                    );


                case CHECK:

                    return executeCheck(
                            page,
                            action,
                            true
                    );


                case UNCHECK:

                    return executeCheck(
                            page,
                            action,
                            false
                    );


                case CLICK:

                    return executeClick(
                            page,
                            action
                    );


                case UPLOAD:

                    return executeUpload(
                            page,
                            action,
                            resume
                    );


                case SUBMIT:

                    return executeSubmit(
                            page,
                            action
                    );


                case WAIT:

                    return executeWait(
                            page,
                            action
                    );


                default:

                    return ActionResult.failed(
                            "Unsupported executable action: "
                                    + action.type()
                    );
            }


        } catch (Exception e) {

            return ActionResult.failed(
                    "Action execution failed: "
                            + (
                            e.getMessage() == null
                                    ? "Unknown error"
                                    : e.getMessage()
                    )
            );
        }
    }


    /*
     * ================================================================
     * FILL
     * ================================================================
     */

    private ActionResult executeFill(
            Page page,
            Action action
    ) {

        Locator locator =
                resolveField(
                        page,
                        action.target()
                );


        if (
                locator == null
                        || locator.count() == 0
        ) {

            return ActionResult.failed(
                    "Field not found: "
                            + action.target()
            );
        }


        locator.fill(
                nonBlank(
                        action.value(),
                        ""
                )
        );


        System.out.println(
                "FILLED: "
                        + action.target()
        );


        return ActionResult.success(
                false
        );
    }


    /*
     * ================================================================
     * SELECT
     * ================================================================
     */

    private ActionResult executeSelect(
            Page page,
            Action action
    ) {

        Locator locator =
                resolveField(
                        page,
                        action.target()
                );


        if (
                locator == null
                        || locator.count() == 0
        ) {

            return ActionResult.failed(
                    "Select field not found: "
                            + action.target()
            );
        }


        String value =
                action.value();


        try {

            locator.selectOption(
                    new com.microsoft.playwright.options.SelectOption()
                            .setLabel(value)
            );


        } catch (Exception first) {

            try {

                locator.selectOption(
                        value
                );


            } catch (Exception second) {

                /*
                 * Some modern ATS controls are custom dropdowns.
                 *
                 * Click and let the page expose the options.
                 */

                locator.click();


                page.waitForTimeout(
                        500
                );


                Locator option =
                        page.getByText(
                                value,
                                new Page.GetByTextOptions()
                                        .setExact(true)
                        ).first();


                if (
                        option.count() == 0
                                || !option.isVisible()
                ) {

                    return ActionResult.failed(
                            "Dropdown option not found: "
                                    + value
                    );
                }


                option.click();
            }
        }


        System.out.println(
                "SELECTED: "
                        + value
        );


        return ActionResult.success(
                false
        );
    }


    /*
     * ================================================================
     * CHECK / UNCHECK
     * ================================================================
     */

    private ActionResult executeCheck(
            Page page,
            Action action,
            boolean checked
    ) {

        Locator locator =
                resolveField(
                        page,
                        action.target()
                );


        if (
                locator == null
                        || locator.count() == 0
        ) {

            return ActionResult.failed(
                    "Checkbox/radio field not found: "
                            + action.target()
            );
        }


        locator.setChecked(
                checked
        );


        System.out.println(
                (checked ? "CHECKED: " : "UNCHECKED: ")
                        + action.target()
        );


        return ActionResult.success(
                false
        );
    }


    /*
     * ================================================================
     * CLICK
     * ================================================================
     */

    private ActionResult executeClick(
            Page page,
            Action action
    ) {

        Locator locator =
                resolveAnyControl(
                        page,
                        action.target()
                );


        if (
                locator == null
                        || locator.count() == 0
        ) {

            return ActionResult.failed(
                    "Clickable control not found: "
                            + action.target()
            );
        }


        locator.click(
                new Locator.ClickOptions()
                        .setTimeout(
                                15_000
                        )
                        .setNoWaitAfter(
                                true
                        )
        );


        System.out.println(
                "CLICKED: "
                        + action.target()
        );


        return ActionResult.success(
                true
        );
    }


    /*
     * ================================================================
     * UPLOAD
     * ================================================================
     */

    private ActionResult executeUpload(
            Page page,
            Action action,
            Path resume
    ) {

        Locator locator =
                resolveField(
                        page,
                        action.target()
                );


        if (
                locator == null
                        || locator.count() == 0
        ) {

            return ActionResult.failed(
                    "Upload field not found: "
                            + action.target()
            );
        }


        try {

            /*
             * Native file input.
             */

            locator.setInputFiles(
                    resume
            );


            System.out.println(
                    "UPLOADED RESUME USING FILE INPUT: "
                            + action.target()
            );


            return ActionResult.success(
                    true
            );


        } catch (Exception nativeUploadError) {

            /*
             * Some ATS pages expose a button rather than the
             * actual file input.
             *
             * Gemini identified the correct target, so Java
             * waits for the file chooser while clicking it.
             */

            try {

                FileChooser chooser =
                        page.waitForFileChooser(
                                new Page.WaitForFileChooserOptions()
                                        .setTimeout(
                                                15_000
                                        ),
                                locator::click
                        );


                chooser.setFiles(
                        resume
                );


                System.out.println(
                        "UPLOADED RESUME USING FILE CHOOSER: "
                                + action.target()
                );


                return ActionResult.success(
                        true
                );


            } catch (Exception chooserError) {

                return ActionResult.failed(
                        "Resume upload failed: "
                                + chooserError.getMessage()
                );
            }
        }
    }


    /*
     * ================================================================
     * SUBMIT
     * ================================================================
     */

    private ActionResult executeSubmit(
            Page page,
            Action action
    ) {

        Locator locator =
                resolveAnyControl(
                        page,
                        action.target()
                );


        if (
                locator == null
                        || locator.count() == 0
        ) {

            return ActionResult.failed(
                    "Submit control not found: "
                            + action.target()
            );
        }


        locator.click(
                new Locator.ClickOptions()
                        .setTimeout(
                                15_000
                        )
                        .setNoWaitAfter(
                                true
                        )
        );


        System.out.println(
                "SUBMIT ACTION EXECUTED."
        );


        return ActionResult.submitted();
    }


    /*
     * ================================================================
     * WAIT
     * ================================================================
     */

    private ActionResult executeWait(
            Page page,
            Action action
    ) {

        int milliseconds =
                parseWait(
                        action.value()
                );


        System.out.println(
                "WAITING "
                        + milliseconds
                        + " ms"
        );


        page.waitForTimeout(
                milliseconds
        );


        return ActionResult.success(
                true
        );
    }


    /*
     * ================================================================
     * RESOLVE FIELD
     * ================================================================
     */

    private Locator resolveField(
            Page page,
            String target
    ) {

        if (
                target == null
                        || target.isBlank()
        ) {

            return null;
        }


        try {

            Locator locator =
                    page.locator(
                            "[data-llm-field-id='"
                                    + escapeAttribute(
                                    target
                            )
                                    + "']"
                    ).first();


            if (
                    locator.count() > 0
            ) {

                return locator;
            }


        } catch (Exception ignored) {
        }


        return null;
    }


    /*
     * ================================================================
     * RESOLVE ANY CONTROL
     * ================================================================
     */

    private Locator resolveAnyControl(
            Page page,
            String target
    ) {

        if (
                target == null
                        || target.isBlank()
        ) {

            return null;
        }


        try {

            Locator field =
                    page.locator(
                            "[data-llm-field-id='"
                                    + escapeAttribute(
                                    target
                            )
                                    + "']"
                    ).first();


            if (
                    field.count() > 0
            ) {

                return field;
            }


        } catch (Exception ignored) {
        }


        try {

            Locator button =
                    page.locator(
                            "[data-llm-button-id='"
                                    + escapeAttribute(
                                    target
                            )
                                    + "']"
                    ).first();


            if (
                    button.count() > 0
            ) {

                return button;
            }


        } catch (Exception ignored) {
        }


        return null;
    }


    /*
     * ================================================================
     * CANDIDATE PROFILE
     * ================================================================
     */

    private String buildCandidateProfile(
            User user
    ) {

        StringBuilder profile =
                new StringBuilder();


        profile
                .append("Name: ")
                .append(
                        safe(
                                user.getName()
                        )
                )
                .append('\n');


        profile
                .append("Email: ")
                .append(
                        safe(
                                user.getEmail()
                        )
                )
                .append('\n');


        profile
                .append("Phone: ")
                .append(
                        user.getPhone() == null
                                ? ""
                                : String.valueOf(
                                user.getPhone()
                        )
                )
                .append('\n');


        profile
                .append("Professional Title: ")
                .append(
                        safe(
                                user.getProfessionalTitle()
                        )
                )
                .append('\n');


        profile
                .append("Location: ")
                .append(
                        safe(
                                user.getLocation()
                        )
                )
                .append('\n');


        profile
                .append("LinkedIn: ")
                .append(
                        safe(
                                user.getLinkedinUrl()
                        )
                )
                .append('\n');


        profile
                .append("GitHub: ")
                .append(
                        safe(
                                user.getGithubUrl()
                        )
                )
                .append('\n');


        profile
                .append("Portfolio: ")
                .append(
                        safe(
                                user.getPortfolioUrl()
                        )
                )
                .append('\n');


        profile
                .append("Skills: ")
                .append(
                        safe(
                                user.getSkills()
                        )
                )
                .append('\n');


        /*
         * User.getUserExperience() is an Integer in the
         * current entity, so explicitly convert it.
         */

        profile
                .append("Experience: ")
                .append(
                        user.getUserExperience() == null
                                ? ""
                                : String.valueOf(
                                user.getUserExperience()
                        )
                )
                .append('\n');


        profile
                .append("Education: ")
                .append(
                        safe(
                                user.getEducation()
                        )
                )
                .append('\n');


        profile
                .append("Projects: ")
                .append(
                        safe(
                                user.getProjects()
                        )
                )
                .append('\n');


        profile
                .append("Certifications: ")
                .append(
                        safe(
                                user.getCertifications()
                        )
                )
                .append('\n');


        profile
                .append("Achievements: ")
                .append(
                        safe(
                                user.getAchievements()
                        )
                )
                .append('\n');


        profile
                .append("Other Details: ")
                .append(
                        safe(
                                user.getOtherDetails()
                        )
                )
                .append('\n');


        return profile.toString();
    }


    /*
     * ================================================================
     * PARSE GEMINI ACTION
     * ================================================================
     */

    private Action parseAction(
            String raw
    ) throws Exception {

        String json =
                raw == null
                        ? ""
                        : raw.trim();


        /*
         * Gemini sometimes returns Markdown JSON.
         */

        if (
                json.startsWith("```")
        ) {

            json =
                    json
                            .replaceFirst(
                                    "^```(?:json)?\\s*",
                                    ""
                            )
                            .replaceFirst(
                                    "\\s*```$",
                                    ""
                            )
                            .trim();
        }


        JsonNode root =
                objectMapper.readTree(
                        json
                );


        String type =
                root.path(
                        "type"
                )
                .asText(
                        "STOP"
                )
                .trim()
                .toUpperCase(
                        Locale.ROOT
                );


        ActionType actionType;


        try {

            actionType =
                    ActionType.valueOf(
                            type
                    );

        } catch (Exception e) {

            actionType =
                    ActionType.STOP;
        }


        return new Action(
                actionType,

                root.path(
                        "target"
                ).asText(""),

                root.path(
                        "value"
                ).asText(""),

                root.path(
                        "reason"
                ).asText("")
        );
    }


    /*
     * ================================================================
     * PAGE SETTLE
     * ================================================================
     */

    private void waitForPageToSettle(
            Page page,
            int milliseconds
    ) {

        try {

            page.waitForTimeout(
                    milliseconds
            );


            try {

                page.waitForLoadState(
                        com.microsoft.playwright.options.LoadState.DOMCONTENTLOADED,
                        new Page.WaitForLoadStateOptions()
                                .setTimeout(
                                        10_000
                                )
                );

            } catch (Exception ignored) {
            }


        } catch (Exception ignored) {
        }
    }


    /*
     * ================================================================
     * HUMAN VERIFICATION
     * ================================================================
     */

    private boolean hasHumanVerification(
            Page page
    ) {

        try {

            String text =
                    safeBodyText(
                            page
                    )
                    .toLowerCase(
                            Locale.ROOT
                    );


            return containsAny(
                    text,

                    "captcha",

                    "verify you are human",

                    "i'm not a robot",

                    "im not a robot",

                    "human verification",

                    "security verification",

                    "cloudflare verification"
            );


        } catch (Exception e) {

            return false;
        }
    }


    /*
     * ================================================================
     * SUCCESS DETECTION
     * ================================================================
     *
     * This is only a safety confirmation after Gemini says DONE
     * or after SUBMIT.
     *
     * It is NOT used to understand form fields.
     * ================================================================
     */

    private boolean isSubmissionSuccessful(
            Page page
    ) {

        try {

            String body =
                    safeBodyText(
                            page
                    )
                    .toLowerCase(
                            Locale.ROOT
                    );


            String url =
                    safeUrl(
                            page
                    )
                    .toLowerCase(
                            Locale.ROOT
                    );


            String title =
                    safeTitle(
                            page
                    )
                    .toLowerCase(
                            Locale.ROOT
                    );


            return containsAny(
                    body,

                    "application submitted",

                    "application has been submitted",

                    "your application was submitted",

                    "successfully applied",

                    "application received",

                    "thanks for applying",

                    "thank you for applying",

                    "thank you for your application",

                    "your application has been received"
            )
                    ||
                    containsAny(
                            url,

                            "confirmation",

                            "thank-you",

                            "thankyou",

                            "application-complete"
                    )
                    ||
                    containsAny(
                            title,

                            "application submitted",

                            "thank you"
                    );


        } catch (Exception e) {

            return false;
        }
    }


    /*
     * ================================================================
     * HELPERS
     * ================================================================
     */

    private String safeUrl(
            Page page
    ) {

        try {

            return page.url();

        } catch (Exception e) {

            return "";
        }
    }


    private String safeTitle(
            Page page
    ) {

        try {

            return page.title();

        } catch (Exception e) {

            return "";
        }
    }


    private String safeBodyText(
            Page page
    ) {

        try {

            return page.locator(
                    "body"
            )
            .innerText();

        } catch (Exception e) {

            return "";
        }
    }


    private String safe(
            String value
    ) {

        return value == null
                ? ""
                : value;
    }


    private String nonBlank(
            String value,
            String fallback
    ) {

        if (
                value == null
                        || value.isBlank()
        ) {

            return fallback;
        }


        return value;
    }


    private int parseWait(
            String value
    ) {

        try {

            int parsed =
                    Integer.parseInt(
                            value
                    );


            return Math.max(
                    500,
                    Math.min(
                            parsed,
                            30_000
                    )
            );


        } catch (Exception e) {

            return ACTION_WAIT_MS;
        }
    }


    private boolean containsAny(
            String value,
            String... values
    ) {

        if (
                value == null
        ) {

            return false;
        }


        for (
                String item : values
        ) {

            if (
                    value.contains(
                            item
                    )
            ) {

                return true;
            }
        }


        return false;
    }


    private String escapeAttribute(
            String value
    ) {

        if (
                value == null
        ) {

            return "";
        }


        return value
                .replace(
                        "\\",
                        "\\\\"
                )
                .replace(
                        "'",
                        "\\'"
                );
    }


    /*
     * ================================================================
     * ENUMS
     * ================================================================
     */

    private enum ActionType {

    FILL,

    SELECT,

    CHECK,

    UNCHECK,

    CLICK,

    UPLOAD,

    WAIT,

    SUBMIT,

    ASK_USER,

    AUTHENTICATION_REQUIRED,

    BLOCKED,

    DONE,

    STOP
}
    private enum ExecutionStatus {

        SUCCESS,

        FAILED,

        SUBMITTED
    }


    /*
     * ================================================================
     * RECORDS
     * ================================================================
     */

    private record PageSnapshot(

            String url,

            String title,

            String bodyText,

            String controls

    ) {
    }


    private record Action(

            ActionType type,

            String target,

            String value,

            String reason

    ) {
    }


    private record ActionResult(

            ExecutionStatus status,

            String message,

            boolean longWait

    ) {

        static ActionResult success(
                boolean longWait
        ) {

            return new ActionResult(
                    ExecutionStatus.SUCCESS,
                    "",
                    longWait
            );
        }


        static ActionResult failed(
                String message
        ) {

            return new ActionResult(
                    ExecutionStatus.FAILED,
                    message,
                    false
            );
        }


        static ActionResult submitted() {

            return new ActionResult(
                    ExecutionStatus.SUBMITTED,
                    "Submit action executed.",
                    true
            );
        }
    }


    /*
     * ================================================================
     * PUBLIC RESULT
     * ================================================================
     */

    public record Result(

            boolean success,

            boolean blocked,

            boolean userInputRequired,

            boolean authenticationRequired,

            String message,

            String submittedUrl

    ) {

        public static Result success(
                String message,
                String url
        ) {

            return new Result(
                    true,
                    false,
                    false,
                    false,
                    message,
                    url
            );
        }


        public static Result failed(
                String message
        ) {

            return new Result(
                    false,
                    false,
                    false,
                    false,
                    message,
                    ""
            );
        }


        public static Result blocked(
                String message
        ) {

            return new Result(
                    false,
                    true,
                    false,
                    false,
                    message,
                    ""
            );
        }


        public static Result userInputRequired(
                String message
        ) {

            return new Result(
                    false,
                    false,
                    true,
                    false,
                    message,
                    ""
            );
        }


        public static Result authenticationRequired(
                String message
        ) {

            return new Result(
                    false,
                    false,
                    false,
                    true,
                    message,
                    ""
            );
        }
    }
}