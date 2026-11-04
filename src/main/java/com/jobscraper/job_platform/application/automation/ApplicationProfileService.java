package com.jobscraper.job_platform.application.automation;

import com.jobscraper.job_platform.resume.GeminiClient;
import com.jobscraper.job_platform.user.User;
import com.jobscraper.job_platform.user.UserRepository;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves application fields using:
 *
 * 1. Complete user information from DB
 * 2. users.other_details
 * 3. All visible application-page information
 * 4. Gemini reasoning
 *
 * Gemini is allowed to derive an answer ONLY when the supplied
 * database information supports that answer.
 *
 * No guessing.
 */
@Service
@RequiredArgsConstructor
public class ApplicationProfileService {

    private static final int MAX_PAGE_SNAPSHOT = 30000;

    private final UserRepository userRepository;
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    /**
     * Main entry point.
     *
     * Collects the complete visible application page,
     * sends it together with the complete user profile to Gemini,
     * then fills the returned answers.
     */
    public ProfilePreparation prepare(Page page, User user) {
        return prepare(page, user, "");
    }

    /** Resolve and apply answers only after a complete form inventory has been captured. */
    public ProfilePreparation prepare(Page page, User user, String inventoryJson) {

        if (page == null || user == null) {
            return new ProfilePreparation(
                    List.of(),
                    List.of(),
                    "Page or user is missing."
            );
        }

        ObjectNode otherDetails = readOtherDetails(user);

        ObjectNode applicationProfile =
                getOrCreateObject(
                        otherDetails,
                        "application_profile"
                );

        ObjectNode discoveredFields =
                getOrCreateObject(
                        applicationProfile,
                        "discovered_fields"
                );

        /*
         * ------------------------------------------------------------
         * 1. BUILD COMPLETE PAGE SNAPSHOT
         * ------------------------------------------------------------
         */

        String pageSnapshot = buildVisiblePageSnapshot(page);
        if (inventoryJson != null && !inventoryJson.isBlank()) {
            pageSnapshot += "\n\nCOMPLETE FORM INVENTORY WITH AVAILABLE OPTIONS (source of truth for option matching):\n"
                    + inventoryJson;
        }

        /*
         * ------------------------------------------------------------
         * 2. BUILD COMPLETE DATABASE PROFILE
         * ------------------------------------------------------------
         */

        String databaseProfile =
                buildCompleteDatabaseProfile(
                        user,
                        otherDetails
                );

        System.out.println(
                "=========================================="
        );

        System.out.println(
                "APPLICATION PROFILE LLM RESOLUTION"
        );

        System.out.println(
                "VISIBLE PAGE SNAPSHOT LENGTH: "
                        + pageSnapshot.length()
        );

        System.out.println(
                "DATABASE PROFILE LENGTH: "
                        + databaseProfile.length()
        );

        System.out.println(
                "=========================================="
        );

        /*
         * ------------------------------------------------------------
         * 3. ASK GEMINI TO RESOLVE ALL FIELDS
         * ------------------------------------------------------------
         */

        List<LLMAnswer> llmAnswers =
                askGeminiForAnswers(
                        page,
                        pageSnapshot,
                        databaseProfile
                );

        /*
         * ------------------------------------------------------------
         * 4. APPLY ANSWERS
         * ------------------------------------------------------------
         */

        List<ResolvedField> filled =
                new ArrayList<>();

        List<MissingField> missing =
                new ArrayList<>();

        /*
         * Save all LLM-discovered fields.
         */
        for (LLMAnswer answer : llmAnswers) {

            String key =
                    canonicalKey(
                            answer.key()
                    );

            if (key.isBlank()) {
                key =
                        canonicalKey(
                                answer.question()
                        );
            }

            if (key.isBlank()) {
                continue;
            }

            saveDiscoveredField(
                    discoveredFields,
                    key,
                    answer.question(),
                    answer.required(),
                    answer.type(),
                    answer.value()
            );

            /*
             * --------------------------------------------------------
             * ANSWER AVAILABLE
             * --------------------------------------------------------
             */

            if (answer.value() != null
                    && !answer.value().isBlank()
                    && answer.canAnswer()) {

                boolean filledSuccessfully =
                        applyAnswer(
                                page,
                                answer
                        );

                if (filledSuccessfully) {

                    saveStoredAnswer(
                            applicationProfile,
                            key,
                            answer.value()
                    );

                    filled.add(
                            new ResolvedField(
                                    key,
                                    answer.question()
                            )
                    );

                    removePending(
                            discoveredFields,
                            key
                    );

                    System.out.println(
                            "LLM FILLED: "
                                    + answer.question()
                                    + " = "
                                    + answer.value()
                    );

                    continue;
                }

                /*
                 * Gemini gave an answer but Playwright could not
                 * locate/fill the field.
                 */
                System.out.println(
                        "LLM ANSWER COULD NOT BE APPLIED: "
                                + answer.question()
                );
            }

            /*
             * --------------------------------------------------------
             * NO SAFE ANSWER
             * --------------------------------------------------------
             */

            if (answer.required()) {

                missing.add(
                        new MissingField(
                                key,
                                answer.question(),
                                true,
                                answer.type()
                        )
                );

                System.out.println(
                        "MISSING PROFILE DETAIL: "
                                + key
                                + " | "
                                + answer.question()
                );
            }
        }

        /*
         * ------------------------------------------------------------
         * 5. ALSO CHECK EXISTING STORED ANSWERS
         * ------------------------------------------------------------
         */

        resolvePreviouslyStoredAnswers(
                page,
                applicationProfile,
                discoveredFields,
                filled
        );

        /*
         * ------------------------------------------------------------
         * 6. SAVE DB
         * ------------------------------------------------------------
         */

        saveOtherDetails(
                user,
                otherDetails
        );

        /*
         * Remove duplicate missing fields.
         */

        // Gemini can omit a field from its response. Reconcile its response against
        // the captured inventory so every still-empty required question is returned
        // for Pending Approvals instead of silently disappearing.
        addUnresolvedRequiredInventoryFields(page, inventoryJson, filled, missing);

        missing =
                deduplicateMissing(
                        missing,
                        filled
                );

        return new ProfilePreparation(
                filled,
                missing,
                missing.isEmpty()
                        ? "All application fields were resolved."
                        : "Some application fields still require information."
        );
    }

    /**
     * Ask Gemini to understand the visible application page and
     * derive answers from the complete DB profile.
     */
    private List<LLMAnswer> askGeminiForAnswers(
            Page page,
            String pageSnapshot,
            String databaseProfile
    ) {

        String prompt = """
                You are the reasoning engine for a browser-based job application system.

                Your task is to inspect the COMPLETE VISIBLE APPLICATION PAGE
                and determine the correct answer for EVERY visible application
                field using ONLY the supplied candidate database profile.

                ================================
                CANDIDATE DATABASE PROFILE
                ================================

                %s

                ================================
                CURRENT APPLICATION PAGE
                ================================

                URL:
                %s

                TITLE:
                %s

                ================================
                VISIBLE PAGE CONTENT
                ================================

                %s

                ================================
                CORE RULE
                ================================

                You may answer a field only when the answer is supported by
                information contained in the candidate database profile.

                You may reason semantically, but only from the supplied candidate database profile.

                OPTION CONSTRAINTS:
                - If a field has an available-options list in the form inventory, return a value that exactly matches one of those options whenever possible.
                - You may map a database answer to an option only when the option preserves the answer's meaning.
                - Never select the nearest option when it could change or exaggerate the candidate's facts.
                - If no option safely represents the supported answer, return canAnswer=false.
                - Preserve already-correct live values; do not request filling a field whose current value already matches the supported answer.
                - Do not fabricate missing facts to force a field to be answerable.
                - Gender is explicitly provided as gender in the profile. Never infer it from the candidate's name.
                - If gender is Male, a title/honorific option may be answered Mr; if Female, use Ms (never Mrs unless explicitly requested by the user).
                - Map education[].endYear only to Year of Graduation. Never use it as experience.
                - Map user_experience 0 to Fresher, 2 to 2-3 years, and 3 to 3-4 years only when those exact meanings/options exist. For other experience values, do not invent a band.
                - For phone country-code dropdowns use +91 for an Indian phone number; fill the phone digits separately, excluding a leading +91 if present.
                - Current employer, current CTC, expected CTC, notice period, and preferred location are unknown unless an explicit saved profile answer exists. Return canAnswer=false for them; do not infer from past experience or choose a salary/notice/location option.
                - For "How did you hear about us?" choose the exact "Zoho Careers Page" option if it exists.

                Examples:

                Database:
                "education": "B.E Computer Science and Engineering"
                Page:
                "Educational Qualification"
                Answer:
                "B.E Computer Science and Engineering"

                Database:
                "education": "Government College of Engineering Bargur"
                Page:
                "Name of the College"
                Answer:
                "Government College of Engineering Bargur"

                Database:
                "user_experience": 0
                Page:
                "Years of experience with Python"
                Answer:
                "0"

                ================================
                DO NOT GUESS
                ================================

                If the database does not contain enough evidence,
                return canAnswer=false.

                NEVER invent information.

                NEVER create fake experience.

                NEVER invent salary.

                NEVER invent authorization status.

                NEVER invent sponsorship status.

                NEVER invent demographic information.

                NEVER infer sensitive information.

                For sensitive questions, only answer when the exact answer
                is explicitly present in application_profile.answers or
                another explicit trusted DB field.

                Sensitive examples include:
                - work authorization
                - visa
                - sponsorship
                - salary expectations
                - disability
                - veteran status
                - demographic information
                - criminal history
                - security clearance

                ================================
                FIELD TYPES
                ================================

                Return one object for every relevant visible application field.

                type can be:

                text
                textarea
                select
                radio
                checkbox
                file
                other

                Do NOT include:
                - hidden fields
                - CSS infrastructure
                - analytics
                - tracking controls
                - unrelated navigation
                - CAPTCHA infrastructure that is not visibly presented
                - login/password fields

                ================================
                RESUME
                ================================

                For a resume upload field:

                canAnswer=true
                value="UPLOAD_RESUME"

                ================================
                OUTPUT
                ================================

                Return JSON only.

                {
                  "answers": [
                    {
                      "key": "educational_qualification",
                      "question": "Educational Qualification",
                      "type": "text",
                      "target": "Educational Qualification",
                      "value": "B.E Computer Science and Engineering",
                      "canAnswer": true,
                      "required": true,
                      "evidence": "education"
                    }
                  ]
                }

                IMPORTANT:

                The target must describe the visible field or question,
                not a CSS selector.

                Return ONLY valid JSON.
                """.formatted(
                databaseProfile,
                safeUrl(page),
                safeTitle(page),
                pageSnapshot
        );

        try {

            String raw =
                    geminiClient.generate(
                            prompt,
                            5000
                    );

            String json =
                    cleanJson(
                            raw
                    );

            JsonNode root =
                    objectMapper.readTree(
                            json
                    );

            JsonNode answersNode =
                    root.get("answers");

            if (answersNode == null
                    || !answersNode.isArray()) {

                System.out.println(
                        "Gemini returned no application answers."
                );

                return List.of();
            }

            List<LLMAnswer> result =
                    new ArrayList<>();

            for (JsonNode node : answersNode) {

                result.add(
                        new LLMAnswer(
                                text(
                                        node,
                                        "key"
                                ),
                                text(
                                        node,
                                        "question"
                                ),
                                text(
                                        node,
                                        "type"
                                ),
                                text(
                                        node,
                                        "target"
                                ),
                                text(
                                        node,
                                        "value"
                                ),
                                node.path(
                                        "canAnswer"
                                ).asBoolean(false),
                                node.path(
                                        "required"
                                ).asBoolean(false),
                                text(
                                        node,
                                        "evidence"
                                )
                        )
                );
            }

            return result;

        } catch (Exception e) {

            e.printStackTrace();

            System.out.println(
                    "Application profile LLM failed: "
                            + e.getMessage()
            );

            return List.of();
        }
    }

    /**
     * Apply an LLM answer to the visible page.
     */
    private boolean applyAnswer(
            Page page,
            LLMAnswer answer
    ) {

        if (answer == null
                || answer.value() == null
                || answer.value().isBlank()) {

            return false;
        }

        if ("UPLOAD_RESUME".equalsIgnoreCase(
                answer.value()
        )) {
            return false;
        }

        String target =
                answer.target();

        String value =
                answer.value();

        /*
         * Search main page and frames.
         */

        List<Frame> frames =
                new ArrayList<>(
                        page.frames()
                );

        for (Frame frame : frames) {

            try {

                Locator controls =
                        frame.locator(
                                "input:visible, textarea:visible, select:visible"
                        );

                for (int i = 0;
                     i < controls.count();
                     i++) {

                    Locator control =
                            controls.nth(i);

                    String description =
                            describeControl(
                                    control
                            );

                    if (!matchesTarget(
                            target,
                            answer.question(),
                            description
                    )) {
                        continue;
                    }

                    if (isControlAlreadyCorrect(control, value)) {
                        System.out.println("PROFILE FIELD ALREADY CORRECT; SKIP FILL: " + answer.question());
                        return true;
                    }

                    if (fillControl(
                            control,
                            value,
                            answer.type()
                    )) {
                        return true;
                    }
                }

                // Custom dropdowns (including Zoho Lyte controls) are not native
                // input/select elements. Resolve them only after the question/option
                // inventory has been captured.
                try {
                    Locator customControls = frame.locator("lyte-dropdown, [role='combobox'], [aria-haspopup='listbox']");
                    for (int j = 0; j < Math.min(customControls.count(), 200); j++) {
                        Locator custom = customControls.nth(j);
                        if (!custom.isVisible()) continue;
                        String description = describeControl(custom);
                        if (!matchesTarget(target, answer.question(), description)) continue;
                        String current = safeText(custom);
                        if (!current.isBlank() && normalize(current).equals(normalize(value))) return true;
                        if (fillCustomDropdown(page, custom, value)) return true;
                    }
                } catch (Exception customError) {
                    System.out.println("Custom dropdown resolution warning: " + customError.getMessage());
                }

                /*
                 * Radio/checkbox labels often provide the
                 * most reliable target.
                 */

                if ("radio".equalsIgnoreCase(
                        answer.type()
                ) || "checkbox".equalsIgnoreCase(
                        answer.type()
                )) {

                    if (clickOption(
                            frame,
                            answer
                    )) {
                        return true;
                    }
                }

            } catch (Exception e) {

                System.out.println(
                        "Application field fill warning: "
                                + e.getMessage()
                );
            }
        }

        return false;
    }

    private boolean fillCustomDropdown(Page page, Locator dropdown, String requested) {
        try {
            Locator trigger = dropdown;
            Locator candidates = dropdown.locator("[role='combobox'], .lyteDummyEventContainer, [aria-haspopup='listbox'], button, input");
            for (int i = 0; i < candidates.count(); i++) {
                Locator candidate = candidates.nth(i);
                try { if (candidate.isVisible() && candidate.isEnabled()) { trigger = candidate; break; } }
                catch (Exception ignored) { }
            }
            trigger.click();
            page.waitForTimeout(100);
            String wanted = normalize(requested);
            for (Frame optionFrame : page.frames()) {
                Locator options = optionFrame.locator("[role='option'], lyte-drop-item, [id^='Lyte_Drop_Item'], [class*='dropdown-option'], [class*='DropdownOption'], [class*='drop-item'], li");
                for (int i = 0; i < Math.min(options.count(), 300); i++) {
                    Locator option = options.nth(i);
                    try {
                        if (!option.isVisible() || !option.isEnabled()) continue;
                        String optionText = safeText(option);
                        if (!normalize(optionText).equals(wanted)) continue;
                        option.click();
                        page.waitForTimeout(100);
                        String selected = safeText(dropdown);
                        return normalize(selected).contains(wanted) || !option.isVisible();
                    } catch (Exception ignored) { }
                }
            }
            try { trigger.press("Escape"); } catch (Exception ignored) { }
        } catch (Exception e) {
            System.out.println("Unable to select custom dropdown option '" + requested + "': " + e.getMessage());
        }
        return false;
    }

    private boolean isControlAlreadyCorrect(Locator control, String expected) {
        try {
            String tag = safeTag(control).toLowerCase(Locale.ROOT);
            String actual;
            if ("select".equals(tag)) {
                actual = safeText(control.locator("option:checked"));
                if (normalize(actual).equals(normalize(expected))) return true;
                actual = attr(control, "value");
                return normalize(actual).equals(normalize(expected));
            }
            actual = attr(control, "value");
            if (actual.isBlank()) actual = safeText(control);
            return !actual.isBlank() && normalize(actual).equals(normalize(expected));
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean fillControl(
            Locator control,
            String value,
            String type
    ) {

        try {

            String actualType =
                    attr(
                            control,
                            "type"
                    ).toLowerCase(
                            Locale.ROOT
                    );

            String tag =
                    safeTag(
                            control
                    );

            if ("select".equals(tag)) {

                /*
                 * First try exact option value.
                 */

                try {

                    control.selectOption(
                            value
                    );

                    return true;

                } catch (Exception ignored) {
                }

                /*
                 * Then match option text.
                 */

                Locator options =
                        control.locator(
                                "option"
                        );

                for (int i = 0;
                     i < options.count();
                     i++) {

                    Locator option =
                            options.nth(i);

                    String optionText =
                            safeText(
                                    option
                            );

                    String optionValue =
                            attr(
                                    option,
                                    "value"
                            );

                    if (normalize(
                            optionText
                    ).equals(
                            normalize(
                                    value
                            )
                    ) || normalize(
                            optionValue
                    ).equals(
                            normalize(
                                    value
                            )
                    )) {

                        control.selectOption(
                                optionValue
                        );

                        return true;
                    }
                }

                return false;
            }

            if ("checkbox".equals(
                    actualType
            )) {

                boolean desired =
                        isPositive(
                                value
                        );

                boolean checked =
                        control.isChecked();

                if (desired != checked) {
                    control.check(
                            new Locator.CheckOptions()
                                    .setTimeout(5000)
                    );
                }

                return true;
            }

            if ("radio".equals(
                    actualType
            )) {

                return false;
            }

            control.fill(
                    value
            );

            return true;

        } catch (Exception e) {

            System.out.println(
                    "Could not fill field: "
                            + e.getMessage()
            );

            return false;
        }
    }

    private boolean clickOption(
            Frame frame,
            LLMAnswer answer
    ) {

        try {

            Locator labels =
                    frame.locator(
                            "label:visible"
                    );

            for (int i = 0;
                 i < labels.count();
                 i++) {

                Locator label =
                        labels.nth(i);

                String labelText =
                        safeText(
                                label
                        );

                if (!normalize(
                        labelText
                ).contains(
                        normalize(
                                answer.value()
                        )
                )) {
                    continue;
                }

                label.click(
                        new Locator.ClickOptions()
                                .setTimeout(5000)
                );

                return true;
            }

            /*
             * Try visible radio controls by value.
             */

            Locator radios =
                    frame.locator(
                            "input[type='radio']:visible"
                    );

            for (int i = 0;
                 i < radios.count();
                 i++) {

                Locator radio =
                        radios.nth(i);

                String radioValue =
                        attr(
                                radio,
                                "value"
                        );

                String aria =
                        attr(
                                radio,
                                "aria-label"
                        );

                if (normalize(
                        radioValue
                ).equals(
                        normalize(
                                answer.value()
                        )
                ) || normalize(
                        aria
                ).equals(
                        normalize(
                                answer.value()
                        )
                )) {

                    radio.check();

                    return true;
                }
            }

        } catch (Exception e) {

            System.out.println(
                    "Option selection warning: "
                            + e.getMessage()
            );
        }

        return false;
    }

    private void resolvePreviouslyStoredAnswers(
            Page page,
            ObjectNode applicationProfile,
            ObjectNode discoveredFields,
            List<ResolvedField> filled
    ) {

        ObjectNode answers =
                getOrCreateObject(
                        applicationProfile,
                        "answers"
                );

        for (Map.Entry<String, JsonNode> entry
                : answers.properties()) {

            String key =
                    entry.getKey();

            String value =
                    entry.getValue()
                            .asText(
                                    ""
                            );

            if (value.isBlank()) {
                continue;
            }

            JsonNode discovered =
                    discoveredFields.get(
                            key
                    );

            if (discovered == null
                    || !discovered.isObject()) {
                continue;
            }

            String question =
                    discovered.path(
                            "question"
                    ).asText(
                            key
                    );

            String type =
                    discovered.path(
                            "type"
                    ).asText(
                            "text"
                    );

            LLMAnswer answer =
                    new LLMAnswer(
                            key,
                            question,
                            type,
                            question,
                            value,
                            true,
                            discovered.path(
                                    "required"
                            ).asBoolean(
                                    false
                            ),
                            "stored application profile"
                    );

            if (applyAnswer(
                    page,
                    answer
            )) {

                filled.add(
                        new ResolvedField(
                                key,
                                question
                        )
                );

                removePending(
                        discoveredFields,
                        key
                );
            }
        }
    }

    /**
     * Build a complete visible page representation.
     */
    private String buildVisiblePageSnapshot(
            Page page
    ) {

        StringBuilder out =
                new StringBuilder();

        out.append(
                "PAGE URL: "
        ).append(
                safeUrl(page)
        ).append(
                "\nPAGE TITLE: "
        ).append(
                safeTitle(page)
        ).append(
                "\n\n"
        );

        for (Frame frame
                : page.frames()) {

            out.append(
                    "FRAME URL: "
            ).append(
                    frame.url()
            ).append(
                    "\n"
            );

            try {

                String body =
                        frame.locator(
                                "body"
                        ).innerText();

                out.append(
                        "VISIBLE TEXT:\n"
                );

                out.append(
                        truncate(
                                body,
                                15000
                        )
                );

                out.append(
                        "\n\nVISIBLE CONTROLS:\n"
                );

                Locator controls =
                        frame.locator(
                                "input:visible, textarea:visible, select:visible, " +
                                "button:visible, a:visible, [role='button']:visible"
                        );

                int count =
                        Math.min(
                                controls.count(),
                                150
                        );

                for (int i = 0;
                     i < count;
                     i++) {

                    Locator control =
                            controls.nth(i);

                    out.append(
                            "- "
                    ).append(
                            describeControl(
                                    control
                            )
                    ).append(
                            "\n"
                    );
                }

                out.append(
                        "\n"
                );

            } catch (Exception e) {

                out.append(
                        "Unable to inspect frame.\n"
                );
            }
        }

        return truncate(
                out.toString(),
                MAX_PAGE_SNAPSHOT
        );
    }

    /**
     * Complete user DB profile.
     */
    private String buildCompleteDatabaseProfile(
            User user,
            ObjectNode otherDetails
    ) {

        StringBuilder out =
                new StringBuilder();

        out.append(
                "USER TABLE FIELDS\n"
        );

        out.append(
                "user_id: "
        ).append(
                safe(
                        user.getUserId()
                                == null
                                ? ""
                                : String.valueOf(
                                        user.getUserId()
                                )
                )
        ).append(
                "\n"
        );

        out.append(
                "name: "
        ).append(
                safe(
                        user.getName()
                )
        ).append(
                "\n"
        );

        String[] nameParts = safe(user.getName()).trim().isBlank()
                ? new String[0] : safe(user.getName()).trim().split("\\s+");
        out.append("first_name (all name words except final word; if one word, that word): ")
                .append(nameParts.length == 0 ? "" : nameParts.length == 1 ? nameParts[0]
                        : String.join(" ", java.util.Arrays.copyOf(nameParts, nameParts.length - 1))).append("\n");
        out.append("last_name (final name word only; blank if name has one word): ")
                .append(nameParts.length <= 1 ? "" : nameParts[nameParts.length - 1]).append("\n");
        out.append("gender (explicit user-provided value; never infer from name): ")
                .append(safe(user.getGender())).append("\n");

        out.append(
                "email: "
        ).append(
                safe(
                        user.getEmail()
                )
        ).append(
                "\n"
        );

        out.append(
                "phone: "
        ).append(
                safe(
                        user.getPhone()
                )
        ).append(
                "\n"
        );

        out.append(
                "professional_title: "
        ).append(
                safe(
                        user.getProfessionalTitle()
                )
        ).append(
                "\n"
        );

        out.append(
                "location: "
        ).append(
                safe(
                        user.getLocation()
                )
        ).append(
                "\n"
        );

        out.append(
                "linkedin_url: "
        ).append(
                safe(
                        user.getLinkedinUrl()
                )
        ).append(
                "\n"
        );

        out.append(
                "github_url: "
        ).append(
                safe(
                        user.getGithubUrl()
                )
        ).append(
                "\n"
        );

        out.append(
                "portfolio_url: "
        ).append(
                safe(
                        user.getPortfolioUrl()
                )
        ).append(
                "\n"
        );

        out.append(
                "user_experience: "
        ).append(
                user.getUserExperience()
                        == null
                        ? "0"
                        : String.valueOf(
                                user.getUserExperience()
                        )
        ).append(
                "\n"
        );

        out.append(
                "\neducation:\n"
        ).append(
                safe(
                        user.getEducation()
                )
        );
        out.append("\ngraduation_endYear (from education[].endYear): ")
                .append(extractGraduationEndYear(user.getEducation())).append("\n");

        out.append(
                "\nskills:\n"
        ).append(
                safe(
                        user.getSkills()
                )
        );

        out.append(
                "\nexperience:\n"
        ).append(
                safe(
                        user.getExperience()
                )
        );

        out.append(
                "\nprojects:\n"
        ).append(
                safe(
                        user.getProjects()
                )
        );

        out.append(
                "\ncertifications:\n"
        ).append(
                safe(
                        user.getCertifications()
                )
        );

        out.append(
                "\nachievements:\n"
        ).append(
                safe(
                        user.getAchievements()
                )
        );

        out.append(
                "\nother_links:\n"
        ).append(
                safe(
                        user.getOtherLinks()
                )
        );

        out.append(
                "\nother_details:\n"
        ).append(
                otherDetails.toString()
        );

        return out.toString();
    }

    private String extractGraduationEndYear(String educationJson) {
        if (educationJson == null || educationJson.isBlank()) return "";
        try {
            JsonNode root = objectMapper.readTree(educationJson);
            if (root.isArray()) {
                for (JsonNode item : root) {
                    String year = item.path("endYear").asText("").trim();
                    if (!year.isBlank()) return year;
                }
            } else if (root.isObject()) {
                String year = root.path("endYear").asText("").trim();
                if (!year.isBlank()) return year;
            }
        } catch (Exception ignored) {
            // Education may be stored as legacy plain text; do not guess a year.
        }
        return "";
    }

    private String describeControl(
            Locator control
    ) {

        String tag =
                safeTag(
                        control
                );

        String type =
                attr(
                        control,
                        "type"
                );

        String name =
                attr(
                        control,
                        "name"
                );

        String id =
                attr(
                        control,
                        "id"
                );

        String placeholder =
                attr(
                        control,
                        "placeholder"
                );

        String aria =
                attr(
                        control,
                        "aria-label"
                );

        String value =
                attr(
                        control,
                        "value"
                );

        String text =
                safeText(
                        control
                );

        String required =
                attr(
                        control,
                        "required"
                );

        String options =
                "";

        try {

            if ("select".equals(
                    tag
            )) {

                Locator optionNodes =
                        control.locator(
                                "option"
                        );

                List<String> optionValues =
                        new ArrayList<>();

                for (int i = 0;
                     i < optionNodes.count();
                     i++) {

                    Locator option =
                            optionNodes.nth(i);

                    optionValues.add(
                            safeText(
                                    option
                            )
                                    + " [value="
                                    + attr(
                                    option,
                                    "value"
                            )
                                    + "]"
                    );
                }

                options =
                        String.join(
                                " | ",
                                optionValues
                        );
            }
        } catch (Exception ignored) {
        }

        return
                "tag=" + tag
                        + " type=" + type
                        + " name=" + name
                        + " id=" + id
                        + " placeholder=" + placeholder
                        + " aria=" + aria
                        + " value=" + value
                        + " required=" + required
                        + " text=" + truncate(text, 200)
                        + " options=" + truncate(options, 1000);
    }

    private boolean matchesTarget(
            String target,
            String question,
            String description
    ) {

        String t =
                normalize(
                        target
                );

        String q =
                normalize(
                        question
                );

        String d =
                normalize(
                        description
                );

        if (t.isBlank()
                && q.isBlank()) {
            return false;
        }

        return (!t.isBlank()
                && (d.contains(t)
                || t.contains(d)))
                || (!q.isBlank()
                && (d.contains(q)
                || q.contains(d)));
    }

    private void saveDiscoveredField(
            ObjectNode discoveredFields,
            String key,
            String question,
            boolean required,
            String type,
            String answer
    ) {

        ObjectNode field =
                discoveredFields.has(
                        key
                )
                        && discoveredFields.get(
                        key
                ).isObject()
                        ? (ObjectNode)
                        discoveredFields.get(
                                key
                        )
                        : objectMapper.createObjectNode();

        field.put(
                "question",
                safe(question)
        );

        field.put(
                "required",
                required
        );

        field.put(
                "type",
                safe(type)
        );

        if (answer == null
                || answer.isBlank()) {

            if (!field.has(
                    "answer"
            )) {
                field.putNull(
                        "answer"
                );
            }

        } else {

            field.put(
                    "answer",
                    answer
            );
        }

        discoveredFields.set(
                key,
                field
        );
    }

    private void saveStoredAnswer(
            ObjectNode applicationProfile,
            String key,
            String value
    ) {

        ObjectNode answers =
                getOrCreateObject(
                        applicationProfile,
                        "answers"
                );

        answers.put(
                key,
                value
        );
    }

    public List<MissingField> getMissingDetails(
            User user
    ) {

        ObjectNode otherDetails =
                readOtherDetails(
                        user
                );

        ObjectNode applicationProfile =
                getOrCreateObject(
                        otherDetails,
                        "application_profile"
                );

        ObjectNode discoveredFields =
                getOrCreateObject(
                        applicationProfile,
                        "discovered_fields"
                );

        List<MissingField> result =
                new ArrayList<>();

        for (Map.Entry<String, JsonNode> entry
                : discoveredFields.properties()) {

            JsonNode node =
                    entry.getValue();

            if (!node.isObject()) {
                continue;
            }

            JsonNode answer =
                    node.get(
                            "answer"
                    );

            if (answer != null
                    && !answer.isNull()
                    && !answer.asText(
                    ""
            ).isBlank()) {
                continue;
            }

            result.add(
                    new MissingField(
                            entry.getKey(),
                            node.path(
                                    "question"
                            ).asText(
                                    entry.getKey()
                            ),
                            node.path(
                                    "required"
                            ).asBoolean(
                                    false
                            ),
                            node.path(
                                    "type"
                            ).asText(
                                    ""
                            )
                    )
            );
        }

        return result;
    }

    public void saveAnswer(
            User user,
            String key,
            String answer
    ) {
        saveAnswer(user, key, key, answer);
    }

    public void saveAnswer(
            User user,
            String key,
            String question,
            String answer
    ) {

        ObjectNode otherDetails =
                readOtherDetails(
                        user
                );

        ObjectNode applicationProfile =
                getOrCreateObject(
                        otherDetails,
                        "application_profile"
                );

        saveStoredAnswer(
                applicationProfile,
                canonicalKey(
                        key
                ),
                safe(
                        answer
                )
        );

        String canonicalQuestion = canonicalKey(question);
        if (!canonicalQuestion.isBlank() && !canonicalQuestion.equals(canonicalKey(key))) {
            saveStoredAnswer(applicationProfile, canonicalQuestion, safe(answer));
        }

        ObjectNode discoveredFields =
                getOrCreateObject(
                        applicationProfile,
                        "discovered_fields"
                );

        String canonical = canonicalKey(key);

        JsonNode existing =
                discoveredFields.get(
                        canonical
                );

        if (existing != null
                && existing.isObject()) {

            ((ObjectNode) existing).put(
                    "answer",
                    safe(
                            answer
                    )
            );
        }

        ObjectNode pendingQuestions =
                getOrCreateObject(
                        applicationProfile,
                        "pending_questions"
                );

        for (Map.Entry<String, JsonNode> entry : pendingQuestions.properties()) {
            JsonNode candidate = entry.getValue();
            String candidateFieldId = candidate.path("fieldId").asText("");
            String candidateQuestion = candidate.path("question").asText("");
            boolean matchesKey = canonical.equals(canonicalKey(entry.getKey()))
                    || canonical.equals(canonicalKey(candidateFieldId))
                    || canonicalQuestion.equals(canonicalKey(candidateQuestion));
            if (matchesKey && candidate.isObject()) {
                ((ObjectNode) candidate).put("answer", safe(answer));
                ((ObjectNode) candidate).put("status", "ANSWERED");
            }
        }

        saveOtherDetails(
                user,
                otherDetails
        );
    }

    private void removePending(
            ObjectNode discoveredFields,
            String key
    ) {
        discoveredFields.remove(
                key
        );
    }

    private void addUnresolvedRequiredInventoryFields(
            Page page,
            String inventoryJson,
            List<ResolvedField> filled,
            List<MissingField> missing
    ) {
        if (inventoryJson == null || inventoryJson.isBlank()) return;
        try {
            JsonNode root = objectMapper.readTree(inventoryJson);
            JsonNode fields = root.path("fields");
            if (!fields.isArray()) return;

            for (JsonNode field : fields) {
                if (!field.path("required").asBoolean(false)) continue;
                String label = text(field, "label").trim();
                String fieldId = text(field, "fieldId").trim();
                String type = text(field, "type").trim();
                String currentValue = text(field, "currentValue").trim();
                if (label.isBlank() || isNonQuestionControl(label, type)) continue;
                if (!isEmptyOrPlaceholder(currentValue)) continue;
                if (isLiveFieldCurrentlyAnswered(page, fieldId, label)) continue;

                String key = fieldId.isBlank() ? canonicalKey(label) : fieldId;
                String normalizedLabel = canonicalKey(label);
                boolean alreadyFilled = filled.stream().anyMatch(resolved ->
                        canonicalKey(resolved.key()).equals(canonicalKey(key))
                                || canonicalKey(resolved.question()).equals(normalizedLabel));
                if (alreadyFilled) continue;

                boolean alreadyMissing = missing.stream().anyMatch(existing ->
                        canonicalKey(existing.key()).equals(canonicalKey(key))
                                || canonicalKey(existing.question()).equals(normalizedLabel));
                if (alreadyMissing) continue;

                missing.add(new MissingField(key, label, true, type.isBlank() ? "other" : type));
                System.out.println("INVENTORY REQUIRED FIELD UNRESOLVED: " + label + " | id=" + key);
            }
        } catch (Exception e) {
            System.out.println("Could not reconcile required fields from form inventory: " + e.getMessage());
        }
    }

    private boolean isLiveFieldCurrentlyAnswered(Page page, String fieldId, String label) {
        if (page == null) return false;
        String wantedId = safe(fieldId);
        String wantedLabel = canonicalKey(label);
        for (Frame frame : page.frames()) {
            try {
                Locator controls = frame.locator("input, textarea, select, lyte-dropdown, [role='combobox']");
                for (int i = 0; i < Math.min(controls.count(), 300); i++) {
                    Locator control = controls.nth(i);
                    try {
                        if (!control.isVisible()) continue;
                        String id = attr(control, "id");
                        String name = attr(control, "name");
                        if (!wantedId.isBlank() && !wantedId.equals(id) && !wantedId.equals(name)) continue;
                        String actual = "";
                        String tag = safeTag(control).toLowerCase(Locale.ROOT);
                        if (List.of("input", "textarea", "select").contains(tag)) {
                            actual = safe(String.valueOf(control.evaluate("e => e.value ?? ''")));
                            if ("select".equals(tag)) {
                                String selectedText = safeText(control.locator("option:checked"));
                                if (!selectedText.isBlank()) actual = selectedText;
                            }
                        }
                        if (actual.isBlank()) actual = attr(control, "lt-prop-selected");
                        if (actual.isBlank()) actual = safeText(control);
                        if (!wantedId.isBlank()) return !isEmptyOrPlaceholder(actual);
                        if (wantedLabel.equals(canonicalKey(describeControl(control)))) return !isEmptyOrPlaceholder(actual);
                    } catch (Exception ignored) { }
                }
            } catch (Exception ignored) { }
        }
        return false;
    }

    private boolean isEmptyOrPlaceholder(String value) {
        String normalized = normalize(value);
        return normalized.isBlank()
                || normalized.matches("[-–—]+")
                || normalized.matches("(?i)(none|select|choose|please select|select an option|choose an option|n/a|not selected|-- none --|\\-none-)");
    }

    private boolean isNonQuestionControl(String label, String type) {
        String l = normalize(label);
        String t = normalize(type);
        if (t.equals("file") || t.equals("submit") || t.equals("button")) return true;
        return l.equals("clear") || l.equals("submit") || l.equals("submit application")
                || l.equals("cancel") || l.equals("search") || l.startsWith("remove skill")
                || l.startsWith("upload your resume") || l.equals("resume") || l.equals("cv");
    }

    private List<MissingField> deduplicateMissing(
            List<MissingField> missing,
            List<ResolvedField> filled
    ) {

        List<MissingField> result =
                new ArrayList<>();

        for (MissingField field
                : missing) {

            boolean alreadyFilled =
                    filled.stream()
                            .anyMatch(
                                    f ->
                                            f.key().equals(
                                                    field.key()
                                            )
                            );

            boolean duplicate =
                    result.stream()
                            .anyMatch(
                                    f ->
                                            f.key().equals(
                                                    field.key()
                                            )
                            );

            if (!alreadyFilled
                    && !duplicate) {

                result.add(
                        field
                );
            }
        }

        return result;
    }

    private ObjectNode readOtherDetails(
            User user
    ) {

        String json =
                user.getOtherDetails();

        if (json == null
                || json.isBlank()) {

            return objectMapper.createObjectNode();
        }

        try {

            JsonNode node =
                    objectMapper.readTree(
                            json
                    );

            if (node != null
                    && node.isObject()) {

                return (ObjectNode) node;
            }

        } catch (Exception e) {

            System.out.println(
                    "Invalid other_details JSON. "
                            + "Starting with empty object."
            );
        }

        return objectMapper.createObjectNode();
    }

    private void saveOtherDetails(
            User user,
            ObjectNode otherDetails
    ) {

        try {

            user.setOtherDetails(
                    objectMapper.writeValueAsString(
                            otherDetails
                    )
            );

            userRepository.save(
                    user
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Unable to save user other_details",
                    e
            );
        }
    }

    private ObjectNode getOrCreateObject(
            ObjectNode parent,
            String field
    ) {

        JsonNode existing =
                parent.get(
                        field
                );

        if (existing != null
                && existing.isObject()) {

            return (ObjectNode)
                    existing;
        }

        ObjectNode created =
                objectMapper.createObjectNode();

        parent.set(
                field,
                created
        );

        return created;
    }

    private String cleanJson(
            String raw
    ) {

        if (raw == null) {
            return "{}";
        }

        String json =
                raw.trim();

        if (json.startsWith("```")) {

            json =
                    json.replaceFirst(
                            "^```(?:json)?\\s*",
                            ""
                    );

            json =
                    json.replaceFirst(
                            "\\s*```$",
                            ""
                    );
        }

        int start =
                json.indexOf(
                        "{"
                );

        int end =
                json.lastIndexOf(
                        "}"
                );

        if (start >= 0
                && end > start) {

            return json.substring(
                    start,
                    end + 1
            );
        }

        return json;
    }

    private String text(
            JsonNode node,
            String field
    ) {

        JsonNode value =
                node.get(
                        field
                );

        return value == null
                || value.isNull()
                ? ""
                : value.asText(
                        ""
                ).trim();
    }

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

    private String safeText(
            Locator locator
    ) {

        try {
            return locator.innerText()
                    .trim();
        } catch (Exception e) {
            return "";
        }
    }

    private String safeTag(
            Locator locator
    ) {

        try {

            return String.valueOf(
                    locator.evaluate(
                            "el => el.tagName.toLowerCase()"
                    )
            );

        } catch (Exception e) {

            return "";
        }
    }

    private String attr(
            Locator locator,
            String name
    ) {

        try {

            String value =
                    locator.getAttribute(
                            name
                    );

            return value == null
                    ? ""
                    : value.trim();

        } catch (Exception e) {

            return "";
        }
    }

    private String normalize(
            String value
    ) {

        return safe(
                value
        )
                .toLowerCase(
                        Locale.ROOT
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    private String canonicalKey(
            String value
    ) {

        if (value == null) {
            return "";
        }

        return normalize(
                value
        )
                .replaceAll(
                        "[^a-z0-9]+",
                        "_"
                )
                .replaceAll(
                        "^_+|_+$",
                        ""
                );
    }

    private boolean isPositive(
            String value
    ) {

        String normalized =
                normalize(
                        value
                );

        return normalized.equals(
                "yes"
        )
                || normalized.equals(
                "true"
        )
                || normalized.equals(
                "1"
        )
                || normalized.equals(
                "checked"
        );
    }

    private String truncate(
            String value,
            int max
    ) {

        if (value == null) {
            return "";
        }

        if (value.length() <= max) {
            return value;
        }

        return value.substring(
                0,
                max
        ) + "\n...[TRUNCATED]";
    }

    private String safe(
            String value
    ) {
        return value == null
                ? ""
                : value.trim();
    }

    public record ProfilePreparation(
            List<ResolvedField> filledFields,
            List<MissingField> missingFields,
            String message
    ) {}

    public record ResolvedField(
            String key,
            String question
    ) {}

    public record MissingField(
            String key,
            String question,
            boolean required,
            String type
    ) {}

    private record LLMAnswer(
            String key,
            String question,
            String type,
            String target,
            String value,
            boolean canAnswer,
            boolean required,
            String evidence
    ) {}
}
