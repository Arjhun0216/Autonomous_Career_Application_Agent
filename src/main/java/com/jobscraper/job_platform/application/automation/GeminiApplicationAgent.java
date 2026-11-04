package com.jobscraper.job_platform.application.automation;

import com.jobscraper.job_platform.resume.GeminiClient;
import com.jobscraper.job_platform.application.ApplicationQuestion;
import com.jobscraper.job_platform.application.ApplicationQuestionRepository;
import com.jobscraper.job_platform.user.UserRepository;
import com.jobscraper.job_platform.user.User;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Controlled LLM browser agent.
 *
 * FLOW:
 *
 * 1. Inspect current application page.
 * 2. Fill trusted profile data.
 * 3. Upload generated resume.
 * 4. Discover unknown required questions.
 * 5. Temporarily answer only safe text fields when possible.
 * 6. Continue through application pages.
 * 7. Collect unresolved questions.
 * 8. NEVER submit while unresolved required questions exist.
 * 9. After user supplies answers, the same application can be retried.
 *
 * IMPORTANT:
 * The LLM does not have unrestricted browser control.
 * Java validates every action before executing it.
 */
@Service
@RequiredArgsConstructor
public class GeminiApplicationAgent {

    private static final int MAX_STEPS = 80;

    private static final int GEMINI_MAX_TOKENS = 3500;

    private static final int MAX_PAGE_TEXT = 30000;

    private final GeminiClient geminiClient;

    private final ObjectMapper objectMapper;

    private final ApplicationProfileService applicationProfileService;

    private final ApplicationFormInventoryService formInventoryService;

    private final ApplicationQuestionRepository applicationQuestionRepository;

    private final UserRepository userRepository;


    /*
     * ================================================================
     * SIMPLE COMPANY LLM LOOP
     * ================================================================
     *
     * No company action is hard-coded here.
     *
     * Every iteration sends:
     *   COMPANY WORKFLOW + CURRENT LIVE PAGE
     * to Gemini.
     *
     * Gemini returns one browser action. Playwright executes exactly
     * that action. The page is then observed again after 10 seconds.
     */
    private Result runCompanyWorkflow(
            Page page,
            User user,
            String resumePath,
            String coverLetterPath,
            CompanyWorkflow workflow,
            Long applicationId
    ) {
        String previousActionFeedback = "No previous action has been attempted.";
        String previousActionSignature = "";
        int repeatedActionFailures = 0;
        String lastInventoriedUrl = "";
        String lastInventoryFingerprint = "";
        boolean resumeUploaded = false;

        // Company loop uses short action timeouts; a missing selector must not
        // stall the entire run for Playwright's default 30 seconds.
        page.setDefaultTimeout(3500);

        for (int step = 1; step <= MAX_STEPS; step++) {
            try {
                // Capture and print the exact live DOM snapshot used by the LLM.
                // This makes failures diagnosable instead of guessing what Gemini saw.
                String liveMetadata = buildLivePageDetails(page);

                // PHASE 1: inventory the live form and all discoverable options before
                // Gemini starts proposing field-by-field actions. Resume parsing must
                // already be complete when the portal exposes a resume upload control.
                String currentInventoryUrl = safeUrl(page);
                String currentInventoryFingerprint = formInventoryService.structureFingerprint(page);
                boolean formStructureChanged = !currentInventoryFingerprint.equals(lastInventoryFingerprint);
                if (resumeUploaded && (!currentInventoryUrl.equals(lastInventoriedUrl) || formStructureChanged)
                        && formInventoryService.isApplicationFormReady(page)) {
                    String inventoryJson = formInventoryService.discoverAndPersist(page, user, applicationId);
                    lastInventoriedUrl = currentInventoryUrl;
                    lastInventoryFingerprint = formInventoryService.structureFingerprint(page);
                    ApplicationProfileService.ProfilePreparation preparation =
                            applicationProfileService.prepare(page, user, inventoryJson);
                    System.out.println("FORM INVENTORY COMPLETE: fields captured before answer resolution.");
                    System.out.println("DATABASE/GEMINI RESOLVED FIELDS: " + preparation.filledFields().size());
                    System.out.println("UNRESOLVED FIELDS: " + preparation.missingFields().size());
                    AgentAction inventoryQuestions = buildPendingQuestionsAction(
                            preparation, inventoryJson, "Some application questions could not be answered from saved profile data.");
                    if (inventoryQuestions != null) {
                        savePendingQuestions(page, user, applicationId, inventoryQuestions);
                    }
                    liveMetadata = buildLivePageDetails(page);
                }

                System.out.println("========== PLAYWRIGHT LIVE METADATA | STEP " + step + " ==========");
                System.out.println(liveMetadata);
                System.out.println("========== END PLAYWRIGHT LIVE METADATA ==========");

                String prompt = buildCompanyPrompt(
                        page,
                        user,
                        resumePath,
                        coverLetterPath,
                        workflow,
                        applicationId,
                        step,
                        previousActionFeedback,
                        liveMetadata
                );

                String raw = geminiClient.generate(
                        prompt,
                        GEMINI_MAX_TOKENS
                );

                System.out.println("======================================");
                System.out.println("COMPANY GEMINI STEP: " + step);
                System.out.println(raw);
                System.out.println("======================================");

                AgentAction action = parseAction(raw);

                if (action == null) {
                    previousActionFeedback = "Gemini returned invalid JSON/action. Inspect the current live page and return one valid JSON action.";
                    continue;
                }

                // Persist discovered unknown questions on every iteration, not only
                // when Gemini asks to stop. This keeps them available in Pending Approval.
                if (action.questions() != null && action.questions().isArray()
                        && !action.questions().isEmpty()) {
                    savePendingQuestions(page, user, applicationId, action);
                }

                String status = safe(action.status()).toUpperCase(Locale.ROOT);

                if ("AUTHENTICATION_REQUIRED".equals(status)) {
                    return Result.authenticationRequired(
                            messageOrDefault(action.reason(), "Authentication is required.")
                    );
                }

                if ("CAPTCHA_BLOCKED".equals(status)) {
                    return Result.blocked(
                            messageOrDefault(action.reason(), "Human verification is required.")
                    );
                }

                if ("USER_INPUT_REQUIRED".equals(status)) {
                    savePendingQuestions(
                            page,
                            user,
                            applicationId,
                            action
                    );
                    return Result.userInputRequired(
                            messageOrDefault(action.reason(), "User input is required.")
                    );
                }

                if ("COMPLETE".equals(status)) {
                    if (hasVisibleSubmissionConfirmation(page)) {
                        return Result.success(
                                messageOrDefault(action.reason(), "Application submitted and verified."),
                                safeUrl(page)
                        );
                    }
                    previousActionFeedback = "Gemini reported COMPLETE, but the live page does not contain a visible submission confirmation. Do not claim success; inspect the current page and continue.";
                    continue;
                }

                if ("FAILED".equals(status)) {
                    return Result.failed(
                            messageOrDefault(action.reason(), "Company application workflow failed.")
                    );
                }

                String actionSignature = safe(action.action()) + "|" + safe(action.target()) + "|" + safe(action.value());
                if (actionSignature.equals(previousActionSignature) && repeatedActionFailures >= 2) {
                    return Result.failed("Stopped repeated failing browser action after two attempts: "
                            + actionSignature + ". The live target is not actionable; inspect the page or request user intervention.");
                }
                try {
                    String requestedType = safe(action.action()).toUpperCase(Locale.ROOT);
                    if (!resumeUploaded && !"UPLOAD_RESUME".equals(requestedType)
                            && !"WAIT".equals(requestedType)
                            && !("CLICK".equals(requestedType) && isResumeUploadTarget(action))
                            && !("CLICK".equals(requestedType) && isAllowedPreUploadClick(page, workflow, action))) {
                        throw new IllegalStateException("Resume-first rule: upload the generated resume and wait for parsing before any other form action.");
                    }
                    if ("CLICK".equalsIgnoreCase(safe(action.action()))
                            && isFinalSubmissionTarget(page, action.target())
                            && hasSavedPendingRequiredQuestions(applicationId)) {
                        previousActionFeedback = "FINAL SUBMISSION BLOCKED: required unknown questions are saved for Pending Approval. Do not submit. Return USER_INPUT_REQUIRED with all unresolved questions.";
                        return Result.userInputRequired("Application paused before final submission. Required questions were saved for Pending Approval.");
                    }
                    executeCompanyAction(page, resumePath, coverLetterPath, workflow, action);
                    if ("UPLOAD_RESUME".equals(requestedType)) {
                        resumeUploaded = true;
                        lastInventoriedUrl = "";
                        lastInventoryFingerprint = "";
                    }
                    previousActionSignature = actionSignature;
                    repeatedActionFailures = 0;
                    previousActionFeedback = "Last action executed without a Playwright exception: "
                            + actionSignature + ". Re-inspect the new page before deciding what to do next.";
                    // Fast path: ordinary form fills/selections are verified immediately by
                    // Playwright and do not need an artificial 10-second pause. Wait after
                    // actions likely to navigate, reveal controls, or trigger async processing.
                    String executedType = safe(action.action()).toUpperCase(Locale.ROOT);
                    String executedTarget = safe(action.target()).toLowerCase(Locale.ROOT);
                    boolean kekaApplyClick = workflow instanceof KekaWorkflow
                            && "CLICK".equals(executedType) && executedTarget.contains("apply");
                    if (("CLICK".equals(executedType) && !kekaApplyClick)
                            || "UPLOAD_COVER_LETTER".equals(executedType) || "PRESS".equals(executedType)) {
                        page.waitForTimeout(900L);
                    } else if ("UPLOAD_RESUME".equals(executedType)
                            && !(workflow instanceof KekaWorkflow)) {
                        page.waitForTimeout(1500L);
                    }
                    // Page state is re-captured at the top of the next iteration.
                } catch (Exception actionError) {
                    if (actionSignature.equals(previousActionSignature)) {
                        repeatedActionFailures++;
                    } else {
                        previousActionSignature = actionSignature;
                        repeatedActionFailures = 1;
                    }
                    previousActionFeedback = "PREVIOUS ACTION FAILED. Action=" + actionSignature
                            + ". Error=" + actionError.getClass().getSimpleName() + ": "
                            + safe(actionError.getMessage())
                            + ". Do NOT repeat the same action/target. Re-inspect the current live page and choose a different corrective action."
                            + " Consecutive failures for this action=" + repeatedActionFailures + ".";
                    System.out.println("Company action failed (will send error to Gemini): " + previousActionFeedback);
                    // No 10-second sleep after a failed action; Gemini needs the
                    // current state and error immediately to recover intelligently.
                }

            } catch (Exception e) {
                previousActionFeedback = "AGENT ITERATION ERROR: " + e.getClass().getSimpleName()
                        + ": " + safe(e.getMessage())
                        + ". Re-inspect current page and choose a corrective action; do not repeat blindly.";
                System.out.println("Company workflow iteration error: " + previousActionFeedback);
            }
        }

        return Result.failed(
                "Company application workflow reached the maximum number of iterations."
        );
    }

    private boolean isResumeUploadTarget(AgentAction action) {
        String target = (safe(action.target()) + " " + safe(action.reason())).toLowerCase(Locale.ROOT);
        return target.contains("resume") || target.contains("cv") || target.contains("upload")
                || target.contains("attach") || target.contains("browse");
    }

    /** Allows only the small set of page-entry actions needed to expose an upload control. */
    private boolean isAllowedPreUploadClick(Page page, CompanyWorkflow workflow, AgentAction action) {
        String target = safe(action.target()).trim();
        if (target.isBlank()) return false;
        try {
            Locator locator = resolveCompanyTarget(page, target).first();
            if (locator.count() == 0 || !locator.isVisible() || !locator.isEnabled()) return false;
            String label = normalize(safe(locator.innerText()) + " "
                    + safe(locator.getAttribute("aria-label")) + " "
                    + safe(locator.getAttribute("title")) + " "
                    + safe(locator.getAttribute("value")) + " " + target);
            if (label.contains("accept cookies") || label.contains("accept all cookies")
                    || label.contains("allow all cookies") || label.contains("agree to cookies")
                    || label.equals("accept all")) return true;
            if (workflow instanceof KekaWorkflow) {
                return label.equals("apply") || label.startsWith("apply now")
                        || label.contains("apply for this job") || label.contains("apply to this job");
            }
            if (!(workflow instanceof GreenhouseWorkflow)) return false;
            return label.equals("apply") || label.contains("apply for this job")
                    || label.equals("apply now") || label.contains("apply to this job");
        } catch (Exception ignored) {
            return false;
        }
    }

    private void executeCompanyAction(
            Page page,
            String resumePath,
            String coverLetterPath,
            CompanyWorkflow workflow,
            AgentAction action
    ) {
        String type = safe(action.action()).toUpperCase(Locale.ROOT);
        String target = safe(action.target()).trim();

        if (target.isBlank() && !"WAIT".equals(type) && !"DONE".equals(type) && !"STOP".equals(type)) {
            throw new IllegalArgumentException("Gemini returned an empty target for action " + type);
        }

        Locator locator = target.isBlank() ? null : resolveCompanyTarget(page, target);
        if (locator != null) {
            validateTargetMatchesReason(locator.first(), action.reason(), action.value());
        }
        if (locator != null) {
            int matches = locator.count();
            if (matches == 0) throw new IllegalArgumentException("Target does not exist in the current live DOM: " + target);
            Locator visibleMatch = null;
            boolean uploadAction = "UPLOAD_RESUME".equals(type) || "UPLOAD_COVER_LETTER".equals(type);
            for (int i = 0; i < matches; i++) {
                Locator candidate = locator.nth(i);
                try {
                    String candidateTag = safe(String.valueOf(candidate.evaluate("e => e.tagName.toLowerCase()")));
                    String candidateType = safe(candidate.getAttribute("type")).toLowerCase(Locale.ROOT);
                    boolean validHiddenFileInput = uploadAction && "input".equals(candidateTag) && "file".equals(candidateType);
                    if ((candidate.isVisible() || validHiddenFileInput) && candidate.isEnabled()) { visibleMatch = candidate; break; }
                } catch (Exception ignored) { }
            }
            if (visibleMatch == null) throw new IllegalArgumentException("Target exists but no matching control is visible and enabled in the current live DOM: " + target);
            locator = visibleMatch;
        }
        // Enforce idempotency in Java, regardless of what Gemini requests. The live
        // DOM is authoritative; this prevents a repeated FILL/SELECT from rewriting
        // a field that already contains the requested answer.
        if (locator != null && ("FILL".equals(type) || "TYPE".equals(type) || "SELECT".equals(type))) {
            Locator targetControl = locator.first();
            String requested = safe(action.value());
            if (isRequestedValueAlreadySelected(targetControl, requested)) {
                System.out.println("SKIPPED ALREADY-CORRECT FIELD: " + safe(action.target()) + " = " + readControlValue(targetControl));
                return;
            }
        }

        switch (type) {
            case "CLICK" -> locator.first().click();
            case "FILL", "TYPE" -> fillSemanticControl(page, locator.first(), safe(action.value()));
            case "SELECT" -> selectSemanticControl(page, locator.first(), safe(action.value()));
            case "CHECK" -> locator.first().check();
            case "UNCHECK" -> locator.first().uncheck();
            case "PRESS" -> locator.first().press(safe(action.value()));
            case "UPLOAD_RESUME" -> {
                if (resumePath == null || resumePath.isBlank()) {
                    throw new IllegalArgumentException("No generated resume path is available.");
                }
                Path file = Path.of(resumePath).toAbsolutePath().normalize();
                if (!Files.isRegularFile(file)) {
                    throw new IllegalArgumentException("Resume file does not exist: " + file);
                }
                locator.first().setInputFiles(file);
                if (workflow instanceof KekaWorkflow) {
                    page.waitForTimeout(5000L);
                } else {
                    waitForResumeProcessing(page, 600_000L);
                }
            }
            case "UPLOAD_COVER_LETTER" -> {
                if (coverLetterPath == null || coverLetterPath.isBlank()) {
                    throw new IllegalArgumentException("No generated cover letter is available.");
                }
                Path file = Path.of(coverLetterPath).toAbsolutePath().normalize();
                if (!Files.isRegularFile(file)) {
                    throw new IllegalArgumentException("Cover letter file does not exist: " + file);
                }
                locator.first().setInputFiles(file);
            }
            case "WAIT" -> page.waitForTimeout(requestedWaitMillis(action.value()));
            case "DONE", "STOP" -> { /* Only COMPLETE status can end as success. */ }
            default -> throw new IllegalArgumentException("Unsupported Gemini action: " + type);
        }
    }


    /** Prevents Gemini from applying a valid value to the wrong semantic field. */
    private void validateTargetMatchesReason(Locator target, String reason, String value) {
        if (target == null || safe(reason).isBlank()) return;
        String label = "";
        try {
            label = safe(String.valueOf(target.evaluate("""
                    e => {
                      const norm=s=>String(s||'').replace(/\\s+/g,' ').trim();
                      const labelled=(e.getAttribute('aria-labelledby')||'').split(/\\s+/).filter(Boolean)
                        .map(id=>document.getElementById(id)?.innerText||'').join(' ');
                      const labels=(e.labels&&[...e.labels].map(x=>x.innerText).join(' '))||'';
                      if(norm(e.getAttribute('aria-label')||labelled||labels)) return norm(e.getAttribute('aria-label')||labelled||labels);
                      let p=e;
                      for(let d=0;d<5&&p;d++,p=p.parentElement){
                        const direct=[...p.querySelectorAll(':scope > label, :scope > [class*=label], :scope > [class*=Label]')]
                          .map(x=>norm(x.innerText)).filter(Boolean);
                        if(direct.length) return direct.sort((a,b)=>a.length-b.length)[0];
                        const prev=p.previousElementSibling;
                        if(prev && /label/i.test(prev.tagName+' '+prev.className) && norm(prev.innerText)) return norm(prev.innerText);
                      }
                      return '';
                    }
                    """)));
        } catch (Exception ignored) { }
        String l = normalize(label);
        String r = normalize(reason);
        if (l.isBlank()) return;
        boolean reasonGraduation = r.contains("graduation year") || r.contains("year of graduation") || r.contains("graduation date");
        boolean labelGraduation = l.contains("graduation") || l.contains("graduating year");
        boolean reasonExperience = r.contains("experience in years") || r.contains("years of experience") || r.contains("user_experience");
        boolean labelExperience = l.contains("experience") && !l.contains("work authorization");
        boolean reasonCurrentLocation = r.contains("current location");
        boolean labelPreferredLocation = l.contains("preferred location");
        boolean reasonPreferredLocation = r.contains("preferred location");
        boolean labelCurrentLocation = l.contains("current location");
        boolean reasonFirstName = r.contains("first name");
        boolean labelLastName = l.contains("last name");
        boolean reasonLastName = r.contains("last name");
        boolean labelFirstName = l.contains("first name");
        String v = normalize(value);
        boolean valueLooksLikeYear = v.matches("(?:19|20|21)\\d{2}");
        boolean valueLooksLikeExperience = List.of("fresher", "0", "0 years", "0-1 years", "less than 1 year").contains(v);
        if ((reasonGraduation && labelExperience) || (reasonExperience && labelGraduation)
                || (labelExperience && valueLooksLikeYear) || (labelGraduation && valueLooksLikeExperience)
                || (reasonCurrentLocation && labelPreferredLocation) || (reasonPreferredLocation && labelCurrentLocation)
                || (reasonFirstName && labelLastName) || (reasonLastName && labelFirstName)) {
            throw new IllegalArgumentException("Target/field mismatch: Gemini reason refers to '" + reason
                    + "' but the live target label is '" + label + "'. Do not fill/select this target; re-inspect the page and choose the matching field.");
        }
    }

    private boolean isRequestedValueAlreadySelected(Locator locator, String requested) {
        if (locator == null || requested == null || requested.isBlank()) return false;
        String wanted = normalize(requested);
        try {
            String tag = safe(String.valueOf(locator.evaluate("e => e.tagName.toLowerCase()")));
            if ("select".equals(tag)) {
                String selectedText = safe(locator.locator("option:checked").innerText());
                String selectedValue = safe(locator.inputValue());
                return wanted.equals(normalize(selectedText)) || wanted.equals(normalize(selectedValue));
            }
            String value = safe(String.valueOf(locator.evaluate("e => (e.value !== undefined && e.value !== null) ? e.value : ''")));
            if (!value.isBlank() && wanted.equals(normalize(value))) return true;
            String selected = safe(locator.getAttribute("lt-prop-selected"));
            if (!selected.isBlank() && wanted.equals(normalize(selected))) return true;
            String ariaValue = safe(locator.getAttribute("aria-valuetext"));
            if (!ariaValue.isBlank() && wanted.equals(normalize(ariaValue))) return true;
            String text = safe(locator.innerText()).replaceAll("\\s+", " ").trim();
            // Only compare the full visible value; never treat a containing string as a match.
            return !text.isBlank() && wanted.equals(normalize(text));
        } catch (Exception ignored) {
            return false;
        }
    }

    private String readControlValue(Locator locator) {
        try {
            String tag = safe(String.valueOf(locator.evaluate("e => e.tagName.toLowerCase()")));
            if ("input".equals(tag) || "textarea".equals(tag) || "select".equals(tag)) {
                String value = safe(String.valueOf(locator.evaluate("e => e.value ?? ''")));
                if (!value.isBlank()) return value;
            }
        } catch (Exception ignored) { }
        try {
            String value = safe(locator.getAttribute("aria-valuetext"));
            if (!value.isBlank()) return value;
            value = safe(locator.getAttribute("lt-prop-selected"));
            if (!value.isBlank()) return value;
            return safe(locator.innerText()).replaceAll("\\s+", " ").trim();
        } catch (Exception ignored) { return ""; }
    }

    /** Implements the single semantic FILL action across native controls. */
    private void fillSemanticControl(Page page, Locator locator, String value) {
        String tag = safe(String.valueOf(locator.evaluate("e => e.tagName.toLowerCase()")));
        String type = safe(locator.getAttribute("type")).toLowerCase(Locale.ROOT);
        String role = safe(locator.getAttribute("role")).toLowerCase(Locale.ROOT);
        if ("select".equalsIgnoreCase(tag)) {
            selectSemanticControl(page, locator, value);
            return;
        }
        if ("checkbox".equals(type) || "radio".equals(type) || "checkbox".equals(role) || "radio".equals(role)) {
            String normalized = normalize(value);
            boolean shouldCheck = List.of("true", "yes", "1", "checked", "selected").contains(normalized);
            if (shouldCheck) locator.check(); else locator.uncheck();
            return;
        }
        boolean editable = false;
        try { editable = locator.isEditable(); } catch (Exception ignored) { }
        if (editable) {
            locator.fill(value);
            String actual = safe(locator.inputValue());
            if (!actual.equals(value)) throw new IllegalStateException("Field value verification failed after FILL action.");
            return;
        }
        // Unified semantic FILL for custom dropdowns (including Lyte widgets).
        String tagLower = tag.toLowerCase(Locale.ROOT);
        String ariaHasPopup = safe(locator.getAttribute("aria-haspopup")).toLowerCase(Locale.ROOT);
        String ariaControls = safe(locator.getAttribute("aria-controls"));
        if ("combobox".equals(role) || !ariaHasPopup.isBlank() || !ariaControls.isBlank()
                || tagLower.contains("dropdown") || tagLower.contains("drop-button")
                || "button".equals(tagLower) || "div".equals(tagLower) || "span".equals(tagLower)) {
            selectSemanticControl(page, locator, value);
            return;
        }
        throw new IllegalArgumentException("FILL target is not an editable field or recognizable dropdown: tag=" + tag + ", role=" + role);
    }

    /** FILL/SELECT share semantic dropdown handling; native and custom controls are supported. */
    private void selectSemanticControl(Page page, Locator locator, String requested) {
        String tag = safe(String.valueOf(locator.evaluate("e => e.tagName.toLowerCase()")));
        if ("select".equalsIgnoreCase(tag)) {
            Object matchResult = locator.evaluate("""
                    (el, requested) => {
                      const norm = s => String(s || '').trim().toLowerCase().replace(/\\s+/g, ' ');
                      const wanted = norm(requested);
                      const opts = [...el.options].filter(o => !o.disabled);
                      let o = opts.find(o => norm(o.value) === wanted);
                      if (!o) o = opts.find(o => norm(o.textContent) === wanted);
                      return o ? o.value : null;
                    }
                    """, requested);
            if (matchResult == null) throw new IllegalArgumentException("Requested option is not present in the live dropdown options: " + requested);
            String matchedValue = String.valueOf(matchResult);
            locator.selectOption(matchedValue);
            String actualValue = safe(locator.inputValue());
            if (!actualValue.equals(matchedValue)) throw new IllegalStateException("Native dropdown selection could not be verified.");
            return;
        }

        // Custom dropdowns frequently render options outside the dropdown element.
        // Click the actual visible trigger, not merely the outer custom-element shell.
        Locator trigger = locator;
        try {
            String triggerSelector = "[role='combobox'], .lyteDummyEventContainer, [aria-haspopup='listbox'], button, input";
            Locator inner = locator.locator(triggerSelector);
            for (int i = 0; i < inner.count(); i++) {
                Locator candidate = inner.nth(i);
                try {
                    if (candidate.isVisible() && candidate.isEnabled()) { trigger = candidate; break; }
                } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
        trigger.click();
        page.waitForTimeout(120);

        String wanted = normalize(requested);
        String optionSelector = "[role='option'], lyte-drop-item, [id^='Lyte_Drop_Item'], [class*='DropdownOption'], [class*='dropdown-option'], [class*='drop-item'], li";
        Locator chosen = null;
        String chosenText = "";
        // Options may be rendered in a different frame or outside the custom dropdown.
        for (Frame frame : page.frames()) {
            Locator options;
            try { options = frame.locator(optionSelector); }
            catch (Exception ignored) { continue; }
            int count;
            try { count = Math.min(options.count(), 500); }
            catch (Exception ignored) { continue; }
            Locator exact = null;
            for (int i = 0; i < count; i++) {
                Locator candidate = options.nth(i);
                try {
                    if (!candidate.isVisible() || !candidate.isEnabled()) continue;
                    String text = safe(candidate.innerText());
                    String normalized = normalize(text);
                    if (normalized.isBlank()) continue;
                    if (normalized.equals(wanted)) { exact = candidate; chosenText = text; break; }
                } catch (Exception ignored) { }
            }
            if (exact != null) { chosen = exact; break; }
        }

        // Last generic fallback: exact visible text in each frame, but never click a hidden match.
        if (chosen == null) {
            for (Frame frame : page.frames()) {
                try {
                    Locator matches = frame.locator("text=\"" + requested.replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
                    for (int i = 0; i < Math.min(matches.count(), 100); i++) {
                        Locator candidate = matches.nth(i);
                        if (candidate.isVisible() && candidate.isEnabled()
                                && normalize(safe(candidate.innerText())).equals(wanted)) {
                            chosen = candidate; chosenText = safe(candidate.innerText()); break;
                        }
                    }
                    if (chosen != null) break;
                } catch (Exception ignored) { }
            }
        }
        if (chosen == null) {
            String liveOptions = visibleOptionSummary(page, optionSelector);
            throw new IllegalArgumentException("Could not find a visible custom-dropdown option matching '" + requested
                    + "'. Visible options: " + liveOptions + ". Dropdown was opened; re-inspect the current live page.");
        }
        chosen.click();
        // Verify selection using the displayed value or selected/active state.
        page.waitForTimeout(100);
        String displayed = "";
        try { displayed = safe(locator.innerText()); } catch (Exception ignored) { }
        if (!normalize(displayed).contains(wanted)) {
            try { displayed = safe(String.valueOf(locator.evaluate("e => e.value || e.getAttribute('value') || e.getAttribute('aria-label') || ''"))); }
            catch (Exception ignored) { }
        }
        // Some widgets update a hidden input while showing a formatted label. The click itself
        // is accepted only after the option became non-visible or the widget reports its text.
        boolean optionStillVisible = false;
        try { optionStillVisible = chosen.isVisible(); } catch (Exception ignored) { }
        if (!normalize(displayed).contains(wanted) && optionStillVisible) {
            throw new IllegalStateException("Clicked custom dropdown option '" + chosenText
                    + "' but could not verify that the dropdown accepted the selection.");
        }
    }

    private String visibleOptionSummary(Page page, String selector) {
        List<String> values = new ArrayList<>();
        for (Frame frame : page.frames()) {
            try {
                Locator options = frame.locator(selector);
                for (int i = 0; i < Math.min(options.count(), 100); i++) {
                    Locator item = options.nth(i);
                    try {
                        if (item.isVisible()) {
                            String text = normalize(safe(item.innerText()));
                            if (!text.isBlank() && !values.contains(text)) values.add(text);
                        }
                    } catch (Exception ignored) { }
                }
            } catch (Exception ignored) { }
        }
        return String.join(" | ", values.stream().limit(30).toList());
    }

    /**
     * Polls for resume parsing completion for at most ten minutes. It exits early
     * when a seen processing indicator disappears, or when a visible parse/upload
     * error appears. If the portal parses silently, it waits up to the ten-minute cap.
     */
    private void waitForResumeProcessing(Page page, long maxWaitMillis) {
        long deadline = System.currentTimeMillis() + maxWaitMillis;
        boolean sawProcessing = false;
        while (System.currentTimeMillis() < deadline) {
            page.waitForTimeout(5000);
            String body;
            try { body = page.locator("body").innerText().toLowerCase(Locale.ROOT); }
            catch (Exception e) { body = ""; }
            boolean processing = body.contains("parsing resume") || body.contains("processing resume")
                    || body.contains("extracting information") || body.contains("uploading resume")
                    || body.contains("please wait") || body.contains("processing...")
                    || body.contains("reading your resume");
            boolean error = body.contains("failed to parse") || body.contains("resume parsing failed")
                    || body.contains("invalid resume") || body.contains("unsupported file")
                    || body.contains("upload failed");
            if (processing) sawProcessing = true;
            if (error) throw new IllegalStateException("The page reports a resume upload or parsing error. Current page state must be sent to Gemini.");
            // A processing indicator was seen and has now disappeared: allow the next
            // iteration to inspect extracted fields rather than waiting for the full cap.
            if (sawProcessing && !processing) return;
            // Some portals parse silently without a visible spinner. In that case keep
            // polling for the full configured cap instead of assuming completion early.
        }
        throw new IllegalStateException("Resume extraction did not visibly finish within ten minutes. Re-inspect the page and report the current upload/parsing state to Gemini.");
    }

    /**
     * Generic target syntax returned by Gemini; this contains no company-specific selectors:
     * text:<visible text>, label:<accessible label>, placeholder:<placeholder>, css:<one CSS selector>.
     * Legacy single Playwright selectors are accepted for compatibility. Comma-separated selector
     * guesses are intentionally rejected because mixing selector engines creates invalid selectors.
     */
    private Locator resolveCompanyTarget(Page page, String target) {
        String requested = target == null ? "" : target.trim();
        // Metadata enumerates all frames. Allow Gemini to name the frame explicitly
        // as frame:<zero-based-index>|<normal target syntax>.
        if (requested.startsWith("frame:")) {
            int separator = requested.indexOf('|');
            if (separator < 0) {
                throw new IllegalArgumentException("Frame target must use frame:<index>|<target>, for example frame:1|label:Email.");
            }
            int frameIndex;
            try {
                frameIndex = Integer.parseInt(requested.substring("frame:".length(), separator).trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Invalid frame index in target: " + requested);
            }
            List<Frame> frames = page.frames();
            if (frameIndex < 0 || frameIndex >= frames.size()) {
                throw new IllegalArgumentException("Frame index " + frameIndex + " is no longer present. Re-inspect the current page.");
            }
            return resolveFrameTarget(frames.get(frameIndex), requested.substring(separator + 1).trim());
        }
        return resolveFrameTarget(page.mainFrame(), requested);
    }

    private Locator resolveFrameTarget(Frame frame, String target) {
        if (target.startsWith("text:")) {
            String text = target.substring("text:".length()).trim();
            // Prefer the actual semantic interactive control over a nested text node
            // such as Lyte's <lyte-yield>. Fall back to visible text for non-controls.
            Locator buttons = frame.getByRole(AriaRole.BUTTON,
                    new Frame.GetByRoleOptions().setName(text).setExact(true));
            if (buttons.count() == 1) return buttons;
            Locator links = frame.getByRole(AriaRole.LINK,
                    new Frame.GetByRoleOptions().setName(text).setExact(true));
            if (links.count() == 1) return links;
            return frame.getByText(text, new Frame.GetByTextOptions().setExact(true));
        }
        if (target.startsWith("label:")) {
            return frame.getByLabel(target.substring("label:".length()).trim());
        }
        if (target.startsWith("placeholder:")) {
            return frame.getByPlaceholder(target.substring("placeholder:".length()).trim());
        }
        if (target.startsWith("css:")) {
            return frame.locator(target.substring("css:".length()).trim());
        }
        if (target.contains(",")) {
            throw new IllegalArgumentException("Gemini returned multiple mixed selectors. Return ONE target using text:, label:, placeholder:, or css: syntax. Target=" + target);
        }
        return frame.locator(target);
    }

    private void savePendingQuestions(Page page, User user, Long applicationId, AgentAction action) {
        if (user == null || applicationId == null || action == null) {
            return;
        }

        try {
            JsonNode questionsNode = action.questions();
            List<JsonNode> questions = new ArrayList<>();
            if (questionsNode != null && questionsNode.isArray()) {
                questionsNode.forEach(questions::add);
            }

            if (questions.isEmpty()) {
                ObjectNode fallback = objectMapper.createObjectNode();
                String fallbackQuestion = messageOrDefault(action.reason(), "Additional application information is required.");
                if (fallbackQuestion.matches("(?i)question[_\\- ]?\\d+")) fallbackQuestion = "Please provide the required information for this application.";
                fallback.put("question", fallbackQuestion);
                fallback.put("fieldId", "");
                fallback.put("type", "other");
                fallback.put("required", true);
                questions.add(fallback);
            }

            JsonNode otherNode;
            try {
                otherNode = user.getOtherDetails() == null || user.getOtherDetails().isBlank()
                        ? objectMapper.createObjectNode()
                        : objectMapper.readTree(user.getOtherDetails());
            } catch (Exception ignored) {
                otherNode = objectMapper.createObjectNode();
            }
            ObjectNode otherDetails = otherNode != null && otherNode.isObject()
                    ? (ObjectNode) otherNode
                    : objectMapper.createObjectNode();
            JsonNode appProfileNode = otherDetails.get("application_profile");
            ObjectNode appProfile;
            if (appProfileNode != null && appProfileNode.isObject()) {
                appProfile = (ObjectNode) appProfileNode;
            } else {
                appProfile = objectMapper.createObjectNode();
                otherDetails.set("application_profile", appProfile);
            }

            JsonNode pendingNode = appProfile.get("pending_questions");
            ObjectNode pending;
            if (pendingNode != null && pendingNode.isObject()) {
                pending = (ObjectNode) pendingNode;
            } else {
                pending = objectMapper.createObjectNode();
                appProfile.set("pending_questions", pending);
            }

            List<ApplicationQuestion> existingQuestions = applicationQuestionRepository
                    .findByApplicationIdOrderByQuestionIdAsc(applicationId);
            for (JsonNode q : questions) {
                String question = q.path("question").asText("").trim();
                String fieldId = q.path("fieldId").asText("").trim();
                question = resolveHumanQuestion(page, fieldId, question);
                if (question.isBlank()) continue;
                // Gemini sometimes echoes synthetic IDs such as question_12345 instead
                // of the actual visible question. Never show those IDs to the user.
                if (question.matches("(?i)question[_\\- ]?\\d+")) {
                    question = resolveHumanQuestion(page, fieldId, "");
                    if (question.isBlank()) question = "Please provide the required information for this application field.";
                }
                String key = canonicalQuestionKey(fieldId.isBlank() ? question : fieldId);

                String temporaryAnswerForRecord = q.path("temporaryAnswer").asText("").trim();
                if (temporaryAnswerForRecord.isBlank() && q.path("temporaryUsed").asBoolean(false)) {
                    temporaryAnswerForRecord = "PENDING_USER_INPUT";
                }
                final String normalizedQuestion = question;
                final String normalizedFieldId = fieldId;
                ApplicationQuestion existing = existingQuestions.stream()
                        .filter(existingQuestion -> normalizedQuestion.equalsIgnoreCase(existingQuestion.getQuestion())
                                || (!normalizedFieldId.isBlank() && normalizedFieldId.equalsIgnoreCase(safe(existingQuestion.getFieldId()))))
                        .findFirst().orElse(null);
                if (isKnownProfileQuestion(question, user)) {
                    if (existing != null && "PENDING".equalsIgnoreCase(safe(existing.getStatus()))) {
                        existing.setStatus("RESOLVED");
                        existing.setQuestion(question);
                        applicationQuestionRepository.save(existing);
                    }
                    continue;
                }
                // Preserve a user's saved answer on reapply; never recreate the same question as PENDING.
                if (existing != null && ("ANSWERED".equalsIgnoreCase(safe(existing.getStatus()))
                        || "RESOLVED".equalsIgnoreCase(safe(existing.getStatus())))) {
                    continue;
                }
                if (existing == null) {
                    ApplicationQuestion entity = ApplicationQuestion.builder()
                            .applicationId(applicationId)
                            .question(question)
                            .fieldId(fieldId)
                            .questionType(q.path("type").asText("other"))
                            .temporaryAnswer(temporaryAnswerForRecord)
                            .availableOptions(optionsAsJson(q.path("options")))
                            .status("PENDING")
                            .required(q.path("required").asBoolean(true))
                            .createdAt(LocalDateTime.now())
                            .build();
                    applicationQuestionRepository.save(entity);
                    existingQuestions.add(entity);
                } else {
                    boolean changed = false;
                    if (!question.equals(existing.getQuestion())) { existing.setQuestion(question); changed = true; }
                    if (!fieldId.isBlank() && !fieldId.equals(existing.getFieldId())) { existing.setFieldId(fieldId); changed = true; }
                    if (!temporaryAnswerForRecord.isBlank() && !temporaryAnswerForRecord.equals(existing.getTemporaryAnswer())) {
                        existing.setTemporaryAnswer(temporaryAnswerForRecord); changed = true;
                    }
                    String availableOptions = optionsAsJson(q.path("options"));
                    if (!availableOptions.isBlank() && !availableOptions.equals(existing.getAvailableOptions())) {
                        existing.setAvailableOptions(availableOptions); changed = true;
                    }
                    if (changed) applicationQuestionRepository.save(existing);
                }

                ObjectNode item = objectMapper.createObjectNode();
                item.put("question", question);
                item.put("fieldId", fieldId);
                item.put("type", q.path("type").asText("other"));
                item.put("required", q.path("required").asBoolean(true));
                String temporaryAnswer = q.path("temporaryAnswer").asText("").trim();
                if (temporaryAnswer.isBlank() && q.path("temporaryUsed").asBoolean(false)) {
                    temporaryAnswer = "PENDING_USER_INPUT";
                }
                item.put("temporaryAnswer", temporaryAnswer);
                item.put("answer", "");
                if (q.path("options").isArray()) item.set("options", q.path("options").deepCopy());
                pending.set(key, item);
            }

            String updatedOtherDetails = objectMapper.writeValueAsString(otherDetails);
            if (!updatedOtherDetails.equals(safe(user.getOtherDetails()))) {
                user.setOtherDetails(updatedOtherDetails);
                userRepository.save(user);
            }

        } catch (Exception e) {
            System.out.println("Unable to save pending application questions: " + e.getMessage());
        }
    }

    private AgentAction buildPendingQuestionsAction(
            ApplicationProfileService.ProfilePreparation preparation,
            String inventoryJson,
            String reason) {
        if (preparation == null || preparation.missingFields() == null || preparation.missingFields().isEmpty()) return null;
        try {
            JsonNode inventory = objectMapper.readTree(inventoryJson);
            JsonNode inventoryFields = inventory.path("fields");
            var questions = objectMapper.createArrayNode();
            for (ApplicationProfileService.MissingField missing : preparation.missingFields()) {
                if (!missing.required()) continue;
                ObjectNode question = objectMapper.createObjectNode();
                question.put("question", missing.question());
                question.put("fieldId", missing.key());
                question.put("type", missing.type());
                question.put("required", true);
                question.put("answer", "");
                for (JsonNode field : inventoryFields) {
                    String label = field.path("label").asText("");
                    String id = field.path("fieldId").asText("");
                    if (label.equalsIgnoreCase(missing.question()) || id.equalsIgnoreCase(missing.key())
                            || normalize(label).equals(normalize(missing.question()))) {
                        if (field.path("options").isArray()) question.set("options", field.path("options").deepCopy());
                        if (!id.isBlank()) question.put("fieldId", id);
                        question.put("type", field.path("type").asText(missing.type()));
                        break;
                    }
                }
                questions.add(question);
            }
            if (questions.isEmpty()) return null;
            return new AgentAction("", "", "", "USER_INPUT_REQUIRED", reason, "", questions);
        } catch (Exception e) {
            System.out.println("Could not build pending question inventory: " + e.getMessage());
            return null;
        }
    }

    private String optionsAsJson(JsonNode options) {
        if (options == null || !options.isArray() || options.isEmpty()) return "";
        try { return objectMapper.writeValueAsString(options); }
        catch (Exception ignored) { return options.toString(); }
    }

    private String resolveHumanQuestion(Page page, String fieldId, String original) {
        String q = safe(original).trim();
        if (!q.isBlank() && !q.matches("(?i)question[_\\- ]?\\d+")) return q;
        if (page == null || safe(fieldId).isBlank()) return "";
        String wanted = fieldId.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        try {
            for (Frame frame : page.frames()) {
                Locator fields = frame.locator("input, textarea, select, [role='combobox'], [aria-controls]");
                for (int i = 0; i < fields.count(); i++) {
                    Locator el = fields.nth(i);
                    String id = safe(el.getAttribute("id"));
                    String name = safe(el.getAttribute("name"));
                    String idNorm = id.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
                    String nameNorm = name.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
                    if (!wanted.equals(idNorm) && !wanted.equals(nameNorm)) continue;
                    String label = safe(String.valueOf(el.evaluate("e => { const norm=s=>(s||'').replace(/\\s+/g,' ').trim(); " +
                            "const id=e.id; let x=id ? document.querySelector('label[for=\"'+CSS.escape(id)+'\"]') : null; " +
                            "if(x && norm(x.innerText)) return norm(x.innerText); " +
                            "if(e.labels && e.labels.length) { const t=[...e.labels].map(l=>norm(l.innerText)).filter(Boolean).join(' '); if(t) return t; } " +
                            "const labelled=e.getAttribute('aria-labelledby'); if(labelled){const t=labelled.split(/\\s+/).map(i=>document.getElementById(i)).filter(Boolean).map(n=>norm(n.innerText)).filter(Boolean).join(' '); if(t)return t;} " +
                            "const selectors='[role=group],.form-group,.field,.lyte-field,.lyteField,.form-field,li,[class*=formElement],[class*=form-element]'; let p=e.closest(selectors); " +
                            "for(let i=0;p && i<4;i++,p=p.parentElement){ const texts=[...p.querySelectorAll('label,[class*=label],[class*=question],[class*=field-name]')].map(n=>norm(n.innerText)).filter(t=>t && t.length<220); if(texts.length){const unique=[...new Set(texts)]; return unique[0];} } " +
                            "return ''; }")));
                    if (!label.isBlank() && !"null".equals(label)) return label;
                    String placeholder = safe(el.getAttribute("placeholder"));
                    if (!placeholder.isBlank()) return placeholder;
                }
            }
        } catch (Exception ignored) { }
        return "";
    }

    private boolean isKnownProfileQuestion(String question, User user) {
        String q = safe(question).toLowerCase(Locale.ROOT);
        if (user == null) return false;
        if ((q.contains("first name") || q.contains("given name")) && !safe(user.getName()).isBlank()) return true;
        if ((q.contains("last name") || q.contains("surname")) && safe(user.getName()).trim().split("\\s+").length > 1) return true;
        if (q.contains("email") && !safe(user.getEmail()).isBlank()) return true;
        if ((q.contains("phone") || q.contains("mobile")) && !safe(user.getPhone()).isBlank()) return true;
        if ((q.contains("current location") || q.equals("location") || q.contains("city")) && !safe(user.getLocation()).isBlank()) return true;
        if (q.contains("linkedin") && !safe(user.getLinkedinUrl()).isBlank()) return true;
        if (q.contains("github") && !safe(user.getGithubUrl()).isBlank()) return true;
        if (q.contains("portfolio") && !safe(user.getPortfolioUrl()).isBlank()) return true;
        if ((q.contains("work authorization") || q.contains("authorized to work")) && !safe(user.getWorkAuthorization()).isBlank()) return true;
        if (q.contains("military") && !safe(user.getMilitaryStatus()).isBlank()) return true;
        return false;
    }

    private String canonicalQuestionKey(String question) {
        return safe(question).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_|_$", "");
    }

    private String buildCompanyPrompt(
            Page page,
            User user,
            String resumePath,
            String coverLetterPath,
            CompanyWorkflow workflow,
            Long applicationId,
            int step,
            String previousActionFeedback,
            String liveMetadata
    ) {
        return """
                You are the browser automation intelligence for this company.

                COMPANY WORKFLOW:
                %s

                CURRENT STEP: %d
                APPLICATION ID: %s

                PREVIOUS ACTION RESULT / ERROR:
                %s

                CANDIDATE DATABASE PROFILE:
                %s

                GENERATED RESUME PATH:
                %s

                GENERATED COVER LETTER PATH:
                %s

                CURRENT LIVE PAGE:
                URL: %s
                TITLE: %s

                CURRENT PAGE DETAILS:
                %s

                CORE ARCHITECTURE:
                The company workflow is only website knowledge/instructions.
                Do NOT use hard-coded company actions.
                The CURRENT PAGE is the source of truth.
                You decide ONE next browser action from the current page.
                Playwright executes that action.
                After execution, the page is captured again and this SAME
                company workflow is sent to you together with the NEW page.
                Continue through the form to the final review stage, then pause before final submission
                if any unresolved required question is saved. Never submit an application with pending questions.

                CANDIDATE DATA RULES (STRICT VALIDATION):
                - Name is split at the final word: first_name contains preceding words and last_name is the final word. For a one-word name, leave last name blank; never invent one.
                - Use gender_explicit_user_selected only. Never infer gender from the name. Use Mr for Male and Ms for Female when a title is asked.
                - graduation_endYear is the only source for Year of Graduation. Map user_experience 0 to Fresher, 2 to 2-3, and 3 to 3-4 only if an exact compatible option exists.
                - Use the saved country_code for the country-code control and enter phone digits without duplicating that prefix.
                - When the form asks how the candidate heard about Zoho, use the exact Zoho Careers Page option if present.
                - Current employer, current CTC, expected CTC, notice period, and preferred location are missing unless an explicit saved answer exists. Save required ones for Pending Approval; do not guess or choose an arbitrary option.
                - Resolve each field by its actual visible label/question, not by field ID alone.
                - Use answer priority: saved user answer for this question, verified database profile,
                  verified resume information, defensible derivation from verified data, otherwise pending.
                - Never invent a surname from an initial, never substitute Chennai for the stored location,
                  and never confuse graduation year with years of experience.
                - Before every FILL, explain in the JSON reason which exact field label was matched and
                  which trusted source supports the value. If label/meaning is ambiguous, do not fill it.
                - Use the supplied database profile whenever the requested field is present.
                - Do not ask the user for data that already exists in the database.
                - Work authorization country defaults to India when the database does not
                  explicitly contain another country.
                - For the question "How did you hear about us?" / equivalent, use
                  "Website or Career Portals" by default.
                - Do not invent any other value.
                - For sensitive/legal questions, use only explicit database values.
                - If a required question is not answerable from the database or the two
                  defaults above, add it to the questions array immediately.
                - For every unknown required question, emit a questions[] object immediately with the real
                  human-readable question, actual DOM fieldId/name, type, required=true, and answer="".
                  Empty answer is the persisted user-input value in users.other_details.application_profile.pending_questions.
                  Never omit the question merely because the control cannot currently be interacted with.
                - For unknown REQUIRED non-sensitive text fields, enter PENDING_USER_INPUT only when
                  the field accepts a safe temporary placeholder; mark temporaryUsed=true and continue.
                  Do not use that marker in email, number, date, dropdown, checkbox, or other constrained
                  fields. Record those as pending with answer="" and continue only if the form permits it.
                - Never place temporary answers in legal/sensitive fields (including work
                  authorization, sponsorship, citizenship, military or demographic fields).
                  Record those questions and continue only if the site allows it without
                  supplying a fabricated answer. If a required sensitive field blocks progress,
                  return USER_INPUT_REQUIRED with the exact question.
                - Work authorization questions must be included in saved questions whenever
                  the profile does not contain an explicit answer. Country defaults to India;
                  that country default is not an answer to work-authorization eligibility.

                FORM DISCOVERY AND ANSWER RESOLUTION RULES:
                - Before ordinary form filling, Java captures a form inventory containing labels, field IDs, types, current values, required flags, and discoverable options.
                - Treat the persisted inventory as the source of truth for the questions and choices discovered on this form.
                - Resolve answers from saved application_profile.answers and trusted candidate database fields first; Gemini may only semantically map supported data to the question.
                - If a field has available options, choose only an option present in that inventory and only when it preserves the supported answer's meaning.
                - If no supported answer or safe option match exists, return the real question, fieldId, type, required=true, options array, and answer="". Do not guess.
                - Never propose FILL for a field whose current value already matches the supported answer. Java independently skips already-correct values.
                - Unknown required questions are persisted and shown to the user with their available options.

                ACTION RULES:
                - RESUME-FIRST WORKFLOW (MANDATORY): On the application page, if a resume/file-upload control
                  is present and the generated resume has not yet been uploaded/accepted in this application,
                  upload the generated resume as the FIRST application-form action before filling ordinary profile
                  fields. Do not click Next/Submit or fill other fields first, unless the page explicitly requires
                  a preceding consent/authentication action to make the upload control available.
                - After upload, wait until the site's resume parsing/extraction has completed, then refresh the
                  live metadata and use the extracted values to prefill the form. Preserve correct extracted
                  values; correct only missing/incorrect values using trusted database/profile data.
                - Inspect the live page before every decision.
                - Use FILL for text fields and dropdown values. Java inspects the real control type. For a
                  custom dropdown, FILL/SELECT opens the actual visible trigger, chooses the exact matching
                  visible option, and verifies the result in one action. Never type a dropdown answer into
                  an unrelated field or invent a generated option ID.
                - After resume upload, wait for extraction/processing to finish (maximum ten minutes).
                  The browser executor polls and exits early when processing finishes. Then inspect all
                  extracted fields against the database; preserve correct values and correct only missing
                  or incorrect values using trusted data.
                - Do not assume a fixed button name, selector, URL, modal, or form layout.
                - Return ONE target only; never return comma-separated selector guesses.
                - Prefer target syntax: text:<exact visible text>, label:<accessible label>,
                  placeholder:<placeholder text>, or css:<one CSS selector>.
                - The metadata lists frames as FRAME 0, FRAME 1, etc. If a target is inside a
                  non-main frame, prefix it with frame:<index>|, e.g. frame:1|label:Email.
                  Frame indexes are temporary and must be taken from the latest snapshot only.
                - Use text: for visible buttons/links when their exact label is known.
                - Use label: or placeholder: for form fields where available; use css: only
                  when the live DOM shows a specific unique selector.
                - Do not mix CSS syntax with Playwright text= syntax in one target.
                - If the previous action failed, do not repeat it. Choose a corrective action.
                - Match each target's fieldLabel and nearbyContext to the exact question before acting. Never reuse a dropdown ID for a different question. Graduation year and experience in years are different fields. If graduation year is present in the education database data, use that year (for example 2027) in Year of Graduation, not Experience In Years.
                - For a custom dropdown (including lyte-dropdown), do not FILL its outer custom-element shell if it is
                  not actionable. First target its visible inner trigger (role=combobox, input, button, or
                  .lyteDummyEventContainer), click it, then inspect the next live snapshot for visible options.
                  Select only an exact visible option and verify the selected value.
                - If the custom dropdown has no visible/actionable trigger, has no matching visible option, or the
                  action fails, do not repeat the same FILL against the same shell. Capture the real question label,
                  field id/name, control type, and available options in questions[] with answer=""; then continue
                  with other safe fields if possible. If it blocks progress and cannot be answered, return
                  USER_INPUT_REQUIRED so Pending Approval can collect the answer.
                - Use only options shown in the current live page state. If options are not visible, open the correct field first and inspect the updated page on the next iteration.
                - Target only controls explicitly present in the live DOM snapshot. Use their exact current id/name/accessible name.
                - Use FILL for text and dropdown values. For a custom dropdown, target its visible control and supply the exact visible option text.
                - Never repeat the same failed action/target. Prefer progress over unnecessary inspection.
                - Upload the generated resume when the current page requests it.
                - If any cookie/privacy/consent banner or consent request is visible, accept it
                  using the actual visible accept/agree control shown in the current page state
                  before continuing with the application. Do not guess a selector or label.
                - Never enter passwords, OTPs, MFA codes, or CAPTCHA answers.
                - If authentication is required, return AUTHENTICATION_REQUIRED.
                - If human verification is required, return CAPTCHA_BLOCKED.
                - Do not report COMPLETE unless the page itself visibly confirms submission.

                QUESTION COLLECTION AND SAFE PAUSE:
                - Include every newly discovered unresolved required question in the questions
                  array on every response, even when status is CONTINUE.
                - Each question must contain the human-readable visible question/label, never a synthetic
                  identifier such as question_12345. Include nearby section/help text if needed to make
                  the question understandable. fieldId must be the actual DOM id/name, not a generated ID.
                  Include type, required=true, temporaryUsed (true only if a non-sensitive field was
                  actually temporarily filled), and temporaryAnswer (PENDING_USER_INPUT or empty).
                - Do not include a question in the pending list if the field already has a verified value
                  from the database or a saved answer. Do not label known first/last name fields as unknown.
                - Continue to the final review stage after temporary non-sensitive placeholders.
                - If any unresolved required questions remain, NEVER click the final submit button.
                  At final review, return USER_INPUT_REQUIRED with every unresolved question.
                  If no unresolved required questions remain and all required fields have trusted
                  answers, submission may proceed. The Java safety layer blocks submission while
                  saved required questions remain pending.
                - If a mandatory sensitive question prevents proceeding, pause immediately and
                  return USER_INPUT_REQUIRED instead of fabricating an answer.

                RETURN JSON ONLY:
                {
                  "action": "CLICK | FILL | TYPE | SELECT | CHECK | UNCHECK | PRESS | UPLOAD_RESUME | UPLOAD_COVER_LETTER | WAIT | DONE | STOP",
                  "target": "ONE target using text:<text>, label:<label>, placeholder:<text>, css:<one selector>, or frame:<index>|<target>; empty only for WAIT/STOP",
                  "value": "value/text/key when required",
                  "status": "CONTINUE | USER_INPUT_REQUIRED | AUTHENTICATION_REQUIRED | CAPTCHA_BLOCKED | COMPLETE | FAILED",
                  "reason": "brief reason",
                  "questions": [{"question":"visible human-readable question","fieldId":"actual DOM id/name","type":"text|select|checkbox|radio|other","required":true,"temporaryUsed":false,"temporaryAnswer":"","answer":""}]
                  "questions": [
                    {"question": "exact visible question", "fieldId": "id/name if available", "type": "text/select/radio/checkbox/other", "required": true, "temporaryUsed": false, "temporaryAnswer": ""}
                  ]
                }
                """.formatted(
                workflow.getInstructions(),
                step,
                applicationId == null ? "" : applicationId.toString(),
                previousActionFeedback == null ? "No previous action feedback." : previousActionFeedback,
                buildCandidateDatabaseProfile(user),
                safe(resumePath),
                safe(coverLetterPath),
                safeUrl(page),
                safeTitle(page),
                liveMetadata
        );
    }

    private String buildCandidateDatabaseProfile(User user) {
        StringBuilder profile = new StringBuilder();
        profile.append("name=").append(safe(user.getName())).append("\n");
        String[] nameParts = safe(user.getName()).isBlank() ? new String[0] : safe(user.getName()).trim().split("\\s+");
        String fallbackFirst = nameParts.length == 0 ? "" : nameParts.length == 1 ? nameParts[0]
                : String.join(" ", java.util.Arrays.copyOf(nameParts, nameParts.length - 1));
        String fallbackLast = nameParts.length <= 1 ? "" : nameParts[nameParts.length - 1];
        profile.append("first_name=").append(safe(user.getFirstName()).isBlank() ? fallbackFirst : safe(user.getFirstName())).append("\n");
        profile.append("last_name=").append(safe(user.getLastName()).isBlank() ? fallbackLast : safe(user.getLastName())).append("\n");
        profile.append("gender_explicit_user_selected=").append(safe(user.getGender())).append("\n");
        profile.append("email=").append(safe(user.getEmail())).append("\n");
        profile.append("phone=").append(safe(user.getPhone())).append("\n");
        profile.append("country_code=").append(safe(user.getCountryCode())).append("\n");
        profile.append("location=").append(safe(user.getLocation())).append("\n");
        profile.append("user_experience=").append(user.getUserExperience() == null ? "" : user.getUserExperience()).append("\n");
        profile.append("github_url=").append(safe(user.getGithubUrl())).append("\n");
        profile.append("linkedin_url=").append(safe(user.getLinkedinUrl())).append("\n");
        profile.append("portfolio_url=").append(safe(user.getPortfolioUrl())).append("\n");
        profile.append("other_links_json=").append(safe(user.getOtherLinks())).append("\n");
        profile.append("linkedin_from_other_links=").append(extractLinkedInFromOtherLinks(user.getOtherLinks())).append("\n");
        profile.append("education=").append(safe(user.getEducation())).append("\n");
        profile.append("graduation_endYear=").append(extractEducationEndYear(user.getEducation())).append("\n");
        profile.append("education_cgpa=").append(extractEducationValue(user.getEducation(), "cgpa", "cumulative_grade_point_average", "grade_point_average", "gpa")).append("\n");
        profile.append("skills=").append(safe(user.getSkills())).append("\n");
        profile.append("experience=").append(safe(user.getExperience())).append("\n");
        profile.append("projects=").append(safe(user.getProjects())).append("\n");
        profile.append("certifications=").append(safe(user.getCertifications())).append("\n");
        profile.append("achievements=").append(safe(user.getAchievements())).append("\n");
        profile.append("work_authorization=").append(safe(user.getWorkAuthorization())).append("\n");
        profile.append("work_authorization_country=").append(
                safe(user.getWorkAuthorizationCountry()).isBlank() ? "India (default)" : safe(user.getWorkAuthorizationCountry())
        ).append("\n");
        profile.append("visa_sponsorship=").append(safe(user.getVisaSponsorship())).append("\n");
        profile.append("military_status=").append(safe(user.getMilitaryStatus())).append("\n");
        profile.append("government_project_experience=").append(safe(user.getGovernmentProjectExperience())).append("\n");
        profile.append("government_project_details=").append(safe(user.getGovernmentProjectDetails())).append("\n");
        profile.append("other_details_json=\n").append(safe(user.getOtherDetails())).append("\n");
        profile.append("Zoho how_heard_about_us=Zoho Careers Page (select only if this exact available option exists)\n");
        return truncate(profile.toString(), 40000);
    }

    private String extractEducationEndYear(String educationJson) {
        if (educationJson == null || educationJson.isBlank()) return "";
        try {
            JsonNode root = objectMapper.readTree(educationJson);
            if (root.isArray()) {
                for (JsonNode item : root) {
                    String year = item.path("endYear").asText("").trim();
                    if (!year.isBlank()) return year;
                }
            } else if (root.isObject()) return root.path("endYear").asText("").trim();
        } catch (Exception ignored) { }
        return "";
    }

    private String extractEducationValue(String educationJson, String... keyNames) {
        if (educationJson == null || educationJson.isBlank()) return "";
        try {
            JsonNode root = objectMapper.readTree(educationJson);
            List<JsonNode> entries = new ArrayList<>();
            if (root.isArray()) root.forEach(entries::add);
            else if (root.isObject()) entries.add(root);
            for (JsonNode entry : entries) {
                if (!entry.isObject()) continue;
                var fields = entry.properties().iterator();
                while (fields.hasNext()) {
                    var field = fields.next();
                    String normalizedKey = field.getKey().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
                    for (String keyName : keyNames) {
                        if (normalizedKey.equals(keyName.replaceAll("[^a-z0-9]", ""))) {
                            String value = field.getValue().asText("").trim();
                            if (!value.isBlank()) return value;
                        }
                    }
                }
            }
        } catch (Exception ignored) { }
        return "";
    }

    private String extractLinkedInFromOtherLinks(String otherLinksJson) {
        if (otherLinksJson == null || otherLinksJson.isBlank()) return "";
        try {
            JsonNode root = objectMapper.readTree(otherLinksJson);
            List<JsonNode> entries = new ArrayList<>();
            if (root.isArray()) root.forEach(entries::add);
            else if (root.isObject()) entries.add(root);
            for (JsonNode entry : entries) {
                if (!entry.isObject()) continue;
                String label = (entry.path("name").asText("") + " "
                        + entry.path("label").asText("") + " "
                        + entry.path("key").asText(""))
                        .toLowerCase(Locale.ROOT);
                if (label.contains("linkedin")) {
                    for (String key : List.of("url", "value", "link")) {
                        String value = entry.path(key).asText("").trim();
                        if (!value.isBlank()) return value;
                    }
                }
            }
        } catch (Exception ignored) { }
        return "";
    }

    private String buildLivePageDetails(Page page) {
        StringBuilder state = new StringBuilder();
        state.append("CURRENT URL: ").append(safeUrl(page)).append("\n")
                .append("CURRENT TITLE: ").append(safeTitle(page)).append("\n")
                .append("FRAMES AND LIVE DOM CONTROLS (field labels and custom dropdowns included):\n");
        try {
            int frameNumber = 0;
            for (Frame frame : page.frames()) {
                state.append("\n--- FRAME ").append(frameNumber++).append(" URL=")
                        .append(safe(frame.url())).append(" ---\n");
                try {
                    state.append("VISIBLE TEXT:\n")
                            .append(truncate(frame.locator("body").innerText(), 6500))
                            .append("\nVISIBLE FORM FIELDS, CUSTOM WIDGETS, BUTTONS AND OPTIONS:\n");
                    String controls = String.valueOf(frame.locator(
                            "input, textarea, select, button, a, [role='button'], [role='link'], [role='combobox'], [role='option'], [role='radio'], [role='checkbox'], [aria-haspopup], [aria-controls], [tabindex]:not([tabindex='-1']), [onclick], lyte-dropdown, lyte-drop-item"
                    ).evaluateAll("""
                            els => els.filter(e => {
                              const r=e.getBoundingClientRect(), s=getComputedStyle(e);
                              const visible = r.width>0 && r.height>0 && s.visibility!=='hidden' && s.display!=='none' && !e.hidden;
                              // Hidden file inputs still need to be discoverable for Playwright setInputFiles().
                              return visible || (e.tagName==='INPUT' && e.type==='file');
                            }).slice(0,260).map((e,i) => {
                              const norm=s=>String(s||'').replace(/\\s+/g,' ').trim();
                              const labelledBy=(e.getAttribute('aria-labelledby')||'').split(/\\s+/).filter(Boolean)
                                .map(id=>document.getElementById(id)?.innerText||'').join(' ');
                              const labels=(e.labels && [...e.labels].map(x=>x.innerText).join(' '))||'';
                              const nearest=e.closest('label');
                              let context='';
                              let p=e;
                              for(let depth=0; depth<5 && p; depth++,p=p.parentElement){
                                const labs=[...p.querySelectorAll(':scope > label, :scope > .label, :scope > [class*=label], :scope > [class*=Label]')]
                                  .map(x=>x.innerText||'').filter(Boolean);
                                if(labs.length){context=labs.join(' ');break;}
                              }
                              const label=norm(e.getAttribute('aria-label')||e.getAttribute('title')||labelledBy||labels||(nearest?nearest.innerText:'')||context);
                              const opts=e.tagName==='SELECT'?[...e.options].slice(0,60).map(o=>({text:norm(o.text),value:o.value,disabled:o.disabled})):undefined;
                              const nearby=e.matches('lyte-dropdown,[role=combobox],[aria-haspopup]')
                                ? norm(e.parentElement?.innerText||'').slice(0,220):'';
                              return {index:i,tag:e.tagName.toLowerCase(),type:e.type||'',role:e.getAttribute('role')||'',text:norm(e.innerText||e.value||'').slice(0,180),fieldLabel:label.slice(0,180),nearbyContext:nearby,id:e.id||'',name:e.name||'',href:e.href||'',placeholder:e.getAttribute('placeholder')||'',ariaControls:e.getAttribute('aria-controls')||'',ariaLabelledby:e.getAttribute('aria-labelledby')||'',ariaExpanded:e.getAttribute('aria-expanded')||'',tabIndex:e.tabIndex,hasOnclick:!!e.onclick||e.hasAttribute('onclick'),required:!!e.required||e.getAttribute('aria-required')==='true'||!!e.closest('label')?.querySelector('[required]'),disabled:!!e.disabled||e.getAttribute('aria-disabled')==='true',value:e.value||'',checked:!!e.checked,options:opts,outerHTML:e.outerHTML.slice(0,420)};
                            })
                            """));
                    state.append(controls).append("\n");
                } catch (Exception frameError) {
                    state.append("Unable to inspect frame: ").append(safe(frameError.getMessage())).append("\n");
                }
            }
            return truncate(state.toString(), 42000);
        } catch (Exception e) {
            return "Unable to read current page: " + safe(e.getMessage());
        }
    }

    private boolean hasSavedPendingRequiredQuestions(Long applicationId) {
        if (applicationId == null) return false;
        try {
            return applicationQuestionRepository
                    .findByApplicationIdAndStatusOrderByQuestionIdAsc(applicationId, "PENDING")
                    .stream().anyMatch(ApplicationQuestion::isRequired);
        } catch (Exception e) {
            System.out.println("Could not check pending questions before submit: " + safe(e.getMessage()));
            // Fail closed: if question state cannot be verified, do not submit.
            return true;
        }
    }

    private boolean isFinalSubmissionTarget(Page page, String target) {
        String t = normalize(target);
        if (t.equals("submit") || t.contains("submit application")
                || t.contains("send application") || t.contains("final submit")
                || t.contains("finish application") || t.contains("complete application")) {
            return true;
        }
        // A CSS/label target can hide the button's meaning in the target string.
        // Inspect the actual live element so pending questions cannot be bypassed
        // by returning a selector such as css:#submitButton.
        try {
            Locator candidate = resolveCompanyTarget(page, safe(target)).first();
            if (candidate.count() == 0) return false;
            String label = normalize(safe(candidate.innerText()) + " "
                    + safe(candidate.getAttribute("aria-label")) + " "
                    + safe(candidate.getAttribute("title")) + " "
                    + safe(candidate.getAttribute("value")) + " "
                    + safe(candidate.getAttribute("type")));
            String type = normalize(safe(candidate.getAttribute("type")));
            return type.equals("submit") || label.equals("submit")
                    || label.contains("submit application")
                    || label.contains("send application")
                    || label.contains("final submit")
                    || label.contains("finish application")
                    || label.contains("complete application");
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean hasVisibleSubmissionConfirmation(Page page) {
        try {
            String text = normalize(page.locator("body").innerText());
            return text.contains("application submitted")
                    || text.contains("successfully submitted")
                    || text.contains("successfully applied")
                    || text.contains("application has been submitted")
                    || text.contains("thank you for applying")
                    || text.contains("your application was received");
        } catch (Exception e) {
            return false;
        }
    }

    /*
     * ================================================================
     * MAIN AGENT
     * ================================================================
     */

    public Result run(
            Page page,
            User user,
            String resumePath,
            CompanyWorkflow workflow,
            Long applicationId
    ) {
        return run(page, user, resumePath, workflow, applicationId, "");
    }

    public Result run(
            Page page,
            User user,
            String resumePath,
            CompanyWorkflow workflow,
            Long applicationId,
            String coverLetterPath
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

        try {

            page.setDefaultTimeout(30000);

            /*
             * Company workflows use the simple LLM browser loop:
             * company prompt + live page -> Gemini -> Playwright -> wait -> repeat.
             * The legacy Google agent below remains unchanged.
             */
            if (workflow != null) {
                return runCompanyWorkflow(
                        page,
                        user,
                        resumePath,
                        coverLetterPath,
                        workflow,
                        applicationId
                );
            }

            System.out.println(
                    "======================================"
            );

            System.out.println(
                    "CONTROLLED GEMINI APPLICATION AGENT"
            );

            System.out.println(
                    "======================================"
            );


            /*
             * --------------------------------------------------------
             * ITERATIVE APPLICATION LOOP
             * --------------------------------------------------------
             */

            StringBuilder actionFeedback = new StringBuilder();

            for (
                    int step = 1;
                    step <= MAX_STEPS;
                    step++
            ) {

                System.out.println(
                        "======================================"
                );

                System.out.println(
                        "APPLICATION STEP: " + step
                );

                System.out.println(
                        "URL: " + safeUrl(page)
                );

                System.out.println(
                        "TITLE: " + safeTitle(page)
                );

                System.out.println(
                        "======================================"
                );


                /*
                 * ----------------------------------------------------
                 * CAPTCHA / HUMAN VERIFICATION
                 * ----------------------------------------------------
                 */

                if (hasHumanVerification(page)) {

                    return Result.blocked(
                            "Human verification/CAPTCHA detected. "
                                    + "Complete it manually."
                    );
                }


                /*
                 * ----------------------------------------------------
                 * AUTHENTICATION
                 * ----------------------------------------------------
                 */

                if (authenticationRequired(page)) {

                    return Result.authenticationRequired(
                            "Google/application authentication is required. "
                                    + "Complete authentication manually."
                    );
                }


                /*
                 * ----------------------------------------------------
                 * RESOLVE KNOWN PROFILE DATA
                 *
                 * This also discovers unknown fields and stores them
                 * in users.other_details.
                 * ----------------------------------------------------
                 */

                ApplicationProfileService.ProfilePreparation preparation =
                        applicationProfileService.prepare(
                                page,
                                user
                        );

                System.out.println(
                        "TRUSTED PROFILE FIELDS FILLED: "
                                + preparation.filledFields().size()
                );

                System.out.println(
                        "UNKNOWN FIELDS DISCOVERED: "
                                + preparation.missingFields().size()
                );


                for (
                        ApplicationProfileService.MissingField field
                        : preparation.missingFields()
                ) {

                    System.out.println(
                            "DISCOVERED QUESTION: "
                                    + field.key()
                                    + " | "
                                    + field.question()
                                    + " | required="
                                    + field.required()
                                    + " | type="
                                    + field.type()
                    );
                }


                /*
                 * ----------------------------------------------------
                 * BUILD CURRENT PAGE STATE
                 * ----------------------------------------------------
                 */

                String pageState =
                        buildPageState(page);


                /*
                 * ----------------------------------------------------
                 * BUILD LLM PROMPT
                 * ----------------------------------------------------
                 */

                String prompt =
                        buildPrompt(
                                page,
                                user,
                                resumePath,
                                pageState,
                                preparation,
                                step,
                                workflow,
                                actionFeedback.toString()
                        );


                /*
                 * ----------------------------------------------------
                 * ASK GEMINI FOR EXACTLY ONE ACTION
                 * ----------------------------------------------------
                 */

                String raw =
                        geminiClient.generate(
                                prompt,
                                GEMINI_MAX_TOKENS
                        );


                System.out.println(
                        "======================================"
                );

                System.out.println(
                        "GEMINI RESPONSE"
                );

                System.out.println(raw);

                System.out.println(
                        "======================================"
                );


                AgentAction action =
                        parseAction(raw);


                if (action == null) {

                    return Result.failed(
                            "Gemini returned an invalid browser action."
                    );
                }


                /*
                 * ----------------------------------------------------
                 * AUTHENTICATION
                 * ----------------------------------------------------
                 */

                if (
                        "AUTHENTICATION_REQUIRED"
                                .equalsIgnoreCase(
                                        action.status()
                                )
                ) {

                    return Result.authenticationRequired(
                            messageOrDefault(
                                    action.reason(),
                                    "Authentication is required."
                            )
                    );
                }


                /*
                 * ----------------------------------------------------
                 * CAPTCHA
                 * ----------------------------------------------------
                 */

                if (
                        "CAPTCHA_BLOCKED"
                                .equalsIgnoreCase(
                                        action.status()
                                )
                ) {

                    return Result.blocked(
                            messageOrDefault(
                                    action.reason(),
                                    "Human verification is required."
                            )
                    );
                }


                /*
                 * ----------------------------------------------------
                 * EXECUTE ACTION
                 * ----------------------------------------------------
                 */

                Result actionResult =
                        executeAction(
                                page,
                                user,
                                resumePath,
                                action,
                                actionFeedback
                        );


                /*
                 * If action itself requires user input,
                 * return it to ApplicationAgentService.
                 */

                if (actionResult != null) {

                    if (
                            actionResult.userInputRequired()
                    ) {

                        return actionResult;
                    }

                    if (
                            actionResult.authenticationRequired()
                    ) {

                        return actionResult;
                    }

                    if (
                            actionResult.blocked()
                    ) {

                        return actionResult;
                    }

                    if (
                            actionResult.success()
                    ) {

                        return actionResult;
                    }

                    if (
                            !actionResult.message().isBlank()
                    ) {

                        return actionResult;
                    }
                }


                page.waitForTimeout(700);


                /*
                 * ----------------------------------------------------
                 * CHECK CAPTCHA AFTER ACTION
                 * ----------------------------------------------------
                 */

                if (hasHumanVerification(page)) {

                    return Result.blocked(
                            "Human verification appeared during application."
                    );
                }


                /*
                 * ----------------------------------------------------
                 * CHECK WHETHER SUBMISSION OCCURRED
                 * ----------------------------------------------------
                 */

                if (looksLikeSuccessfulSubmission(page)) {

                    /*
                     * HARD SAFETY CHECK:
                     *
                     * Even if the page looks successful, make sure
                     * there are no unresolved required questions.
                     */

                    if (
                            hasPendingRequiredQuestions(user)
                    ) {

                        return Result.userInputRequired(
                                "Application contains unresolved required "
                                        + "questions. Submission is blocked."
                        );
                    }

                    return Result.success(
                            "Application submitted and verified.",
                            safeUrl(page)
                    );
                }
            }


            return Result.failed(
                    "Application agent reached the maximum number of steps."
            );

        } catch (Exception e) {

            e.printStackTrace();

            return Result.failed(
                    "Application automation failed: "
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
     * ACTION EXECUTION
     * ================================================================
     */

    private Result executeAction(
            Page page,
            User user,
            String resumePath,
            AgentAction action,
            StringBuilder actionFeedback
    ) {

        if (actionFeedback != null) {
            actionFeedback.setLength(0);
        }

        String type =
                safe(action.action())
                        .toUpperCase(Locale.ROOT);


        switch (type) {

            /*
             * --------------------------------------------------------
             * WAIT
             * --------------------------------------------------------
             */

            case "WAIT" -> {

                page.waitForTimeout(1500);

                return null;
            }


            /*
             * --------------------------------------------------------
             * UPLOAD RESUME
             * --------------------------------------------------------
             */

            case "UPLOAD_RESUME" -> {

                if (
                        resumePath == null
                                || resumePath.isBlank()
                ) {

                    return Result.failed(
                            "Generated resume path is missing."
                    );
                }


                Path path =
                        Path.of(resumePath);


                if (!Files.exists(path)) {

                    return Result.failed(
                            "Generated resume does not exist: "
                                    + resumePath
                    );
                }


                Locator fileInput =
                        findFileInput(
                                page,
                                action.target()
                        );


                if (fileInput == null) {
                    appendActionFeedback(actionFeedback,
                            "UPLOAD_RESUME failed: resume upload control was not found on the current page.");
                    return null;
                }


                try {

                    fileInput.setInputFiles(path);

                    page.waitForTimeout(1000);

                    System.out.println(
                            "RESUME UPLOADED: "
                                    + path
                    );

                    return null;

                } catch (Exception e) {
                    appendActionFeedback(actionFeedback,
                            "UPLOAD_RESUME failed: " + e.getMessage());
                    return null;
                }
            }


            /*
             * --------------------------------------------------------
             * FILL KNOWN PROFILE FIELD
             * --------------------------------------------------------
             */

            case "FILL_PROFILE" -> {

                String field =
                        safe(action.field())
                                .toUpperCase(Locale.ROOT);


                if (
                        !isAllowedProfileField(field)
                ) {

                    return Result.userInputRequired(
                            "Gemini attempted to use an unsupported "
                                    + "candidate profile field: "
                                    + action.target()
                    );
                }


                String value =
                        profileValue(
                                user,
                                field
                        );


                if (
                        value == null
                                || value.isBlank()
                ) {

                    return Result.userInputRequired(
                            "Candidate profile value is unavailable for: "
                                    + action.target()
                    );
                }


                Locator target =
                        findTarget(
                                page,
                                action.target()
                        );


                if (target == null) {

                    return Result.failed(
                            "Could not locate application field: "
                                    + action.target()
                    );
                }


                try {

                    fillNormalControl(
                            target,
                            value
                    );

                    page.waitForTimeout(500);

                    return null;

                } catch (Exception e) {

                    return Result.failed(
                            "Unable to fill field "
                                    + action.target()
                                    + ": "
                                    + e.getMessage()
                    );
                }
            }


            /*
             * --------------------------------------------------------
             * TEMPORARY ANSWER
             *
             * ONLY safe text inputs.
             *
             * NEVER temporary-answer:
             * - authorization
             * - sponsorship
             * - military
             * - demographic
             * - criminal
             * - disability
             * - veteran
             * - citizenship
             * - security
             * - legal questions
             * - radio/checkbox/select
             * --------------------------------------------------------
             */

            case "TEMPORARY_FILL" -> {

                Locator target =
                        findTarget(
                                page,
                                action.target()
                        );


                if (target == null) {

                    return Result.failed(
                            "Temporary-answer field could not be found: "
                                    + action.target()
                    );
                }


                String question =
                        action.target();


                if (
                        isSensitiveQuestion(question)
                ) {

                    return Result.userInputRequired(
                            "Sensitive required question requires "
                                    + "user input: "
                                    + question
                    );
                }


                if (
                        !isSafeTemporaryTextControl(target)
                ) {

                    return Result.userInputRequired(
                            "This required question cannot safely receive "
                                    + "a temporary answer: "
                                    + question
                    );
                }


                try {

                    target.fill(
                            "PENDING_USER_INPUT"
                    );

                    page.waitForTimeout(400);

                    System.out.println(
                            "TEMPORARY ANSWER ENTERED FOR: "
                                    + question
                    );

                    return null;

                } catch (Exception e) {

                    return Result.userInputRequired(
                            "Could not temporarily fill required question: "
                                    + question
                    );
                }
            }


            /*
             * --------------------------------------------------------
             * CLICK
             * --------------------------------------------------------
             */

            case "CLICK" -> {

                String targetText =
                        safe(action.target());


                if (
                        targetText.isBlank()
                ) {

                    return Result.failed(
                            "Gemini returned a CLICK action without a target."
                    );
                }


                /*
                 * HARD SUBMIT PROTECTION
                 */

                if (
                        isSubmitAction(targetText)
                ) {

                    /*
                     * Refresh discovered fields before submit.
                     */

                    applicationProfileService.prepare(
                            page,
                            user
                    );


                    if (
                            hasPendingRequiredQuestions(user)
                    ) {

                        return Result.userInputRequired(
                                "FINAL SUBMIT BLOCKED: one or more required "
                                        + "application questions are still pending."
                        );
                    }


                    if (
                            hasVisibleRequiredEmptyField(page)
                    ) {

                        return Result.userInputRequired(
                                "FINAL SUBMIT BLOCKED: a visible required "
                                        + "field is still empty."
                        );
                    }
                }


                Locator button =
                        findClickable(
                                page,
                                targetText
                        );


                if (button == null) {
                    appendActionFeedback(actionFeedback,
                            "CLICK failed: could not locate clickable control: " + targetText);
                    return null;
                }


                try {

                    button.click(
                            new Locator.ClickOptions()
                                    .setTimeout(10000)
                                    .setNoWaitAfter(true)
                    );

                } catch (Exception e) {
                    String message = e.getMessage() == null ? "unknown click error" : e.getMessage();
                    appendActionFeedback(actionFeedback,
                            "CLICK failed for target [" + targetText + "]: " + message);
                    System.out.println(
                            "Click warning: " + message
                    );
                    return null;
                }

                page.waitForTimeout(1800);


                if (
                        hasHumanVerification(page)
                ) {

                    return Result.blocked(
                            "Human verification appeared after clicking."
                    );
                }


                return null;
            }


            /*
             * --------------------------------------------------------
             * STOP
             * --------------------------------------------------------
             */

            case "STOP" -> {

                String status =
                        safe(action.status())
                                .toUpperCase(Locale.ROOT);


                String reason =
                        messageOrDefault(
                                action.reason(),
                                "Application requires manual input."
                        );


                if (
                        "AUTHENTICATION_REQUIRED"
                                .equals(status)
                ) {

                    return Result.authenticationRequired(
                            reason
                    );
                }


                if (
                        "CAPTCHA_BLOCKED"
                                .equals(status)
                ) {

                    return Result.blocked(
                            reason
                    );
                }


                return Result.userInputRequired(
                        reason
                );
            }


            default -> {

                return Result.failed(
                        "Unsupported Gemini action: "
                                + action.action()
                );
            }
        }
    }


    /*
     * ================================================================
     * PROMPT
     * ================================================================
     */

    private String buildPrompt(
            Page page,
            User user,
            String resumePath,
            String pageState,
            ApplicationProfileService.ProfilePreparation preparation,
            int step,
            CompanyWorkflow workflow,
            String actionFeedback
    ) {

        StringBuilder questions = new StringBuilder();

        for (ApplicationProfileService.MissingField field
                : preparation.missingFields()) {

            questions
                    .append("- KEY: ").append(field.key()).append("\n")
                    .append("  QUESTION: ").append(field.question()).append("\n")
                    .append("  REQUIRED: ").append(field.required()).append("\n")
                    .append("  TYPE: ").append(field.type()).append("\n\n");
        }

        String workflowInstructions = workflow == null
                ? "NO COMPANY-SPECIFIC WORKFLOW WAS PROVIDED. Use the generic application rules."
                : workflow.getInstructions();

        String feedback = (actionFeedback == null || actionFeedback.isBlank())
                ? "No previous browser action error."
                : actionFeedback;

        return """
                You are the reasoning engine inside a controlled browser
                job-application automation system.

                STEP:
                """ + step + " / " + MAX_STEPS + "\n\n" +
                "COMPANY WORKFLOW / WEBSITE KNOWLEDGE:\n" +
                workflowInstructions + "\n\n" +
                "IMPORTANT ARCHITECTURE:\n" +
                "Java/Playwright controls the browser. You decide exactly ONE next browser action. " +
                "The workflow above is guidance about the website; it is NOT a list of hard-coded actions. " +
                "Observe the CURRENT PAGE STATE and decide what is actually possible now. " +
                "After Java executes your action, the page will be observed again.\n\n" +
                "CANDIDATE:\n" +
                "Name: " + safe(user.getName()) + "\n" +
                "Email: " + safe(user.getEmail()) + "\n" +
                "Phone: " + safe(user.getPhone()) + "\n" +
                "Location: " + safe(user.getLocation()) + "\n" +
                "Professional Title: " + safe(user.getProfessionalTitle()) + "\n\n" +
                "GENERATED RESUME:\n" + safe(resumePath) + "\n\n" +
                "CURRENT PAGE URL:\n" + safeUrl(page) + "\n\n" +
                "CURRENT PAGE TITLE:\n" + safeTitle(page) + "\n\n" +
                "CURRENT PAGE SNAPSHOT:\n" + safe(pageState) + "\n\n" +
                "PREVIOUS ACTION / BROWSER FEEDBACK:\n" + feedback + "\n\n" +
                "DISCOVERED UNKNOWN QUESTIONS:\n" + questions + "\n\n" +
                "ALLOWED ACTIONS:\n\n" +
                "1. FILL_PROFILE\n" +
                "   Use ONLY: FIRST_NAME, LAST_NAME, EMAIL, PHONE, LOCATION, " +
                "LINKEDIN, GITHUB, PORTFOLIO, PROFESSIONAL_TITLE.\n\n" +
                "2. UPLOAD_RESUME\n\n" +
                "3. TEMPORARY_FILL\n" +
                "   Only for a non-sensitive text/textarea field. Use exactly " +
                "PENDING_USER_INPUT as the temporary value.\n\n" +
                "4. CLICK\n" +
                "   Click a visible, relevant control such as cookie consent, " +
                "application entry, Next, Continue, Review, or final Submit.\n\n" +
                "5. WAIT\n\n" +
                "6. STOP\n\n" +
                "RECOVERY RULE:\n" +
                "If the previous browser action failed, DO NOT blindly repeat it. " +
                "Use the browser feedback and current page snapshot to identify what " +
                "blocked the action and choose a corrective action. For example, if a " +
                "cookie-freeze layer blocks a click, find the visible cookie consent " +
                "control and click it first.\n\n" +
                "SECURITY RULES:\n" +
                "- Never invent candidate data.\n" +
                "- Never guess sensitive or legal answers.\n" +
                "- Never invent work authorization, sponsorship, citizenship, military, " +
                "demographic, disability, criminal/legal, education, or employment facts.\n" +
                "- Never enter passwords, OTP, MFA codes, or CAPTCHA answers.\n" +
                "- If authentication is required, return AUTHENTICATION_REQUIRED.\n" +
                "- If human verification is required, return CAPTCHA_BLOCKED.\n" +
                "- If a required question cannot safely be answered, return USER_INPUT_REQUIRED.\n" +
                "- Never claim successful submission without visible confirmation.\n" +
                "- Never submit while Java reports unresolved required questions.\n\n" +
                "JSON ONLY:\n\n" +
                "{\n" +
                "  \"action\": \"FILL_PROFILE | UPLOAD_RESUME | TEMPORARY_FILL | CLICK | WAIT | STOP\",\n" +
                "  \"field\": \"FIRST_NAME | LAST_NAME | EMAIL | PHONE | LOCATION | LINKEDIN | GITHUB | PORTFOLIO | PROFESSIONAL_TITLE | NONE\",\n" +
                "  \"target\": \"visible field/button/control\",\n" +
                "  \"status\": \"CONTINUE | USER_INPUT_REQUIRED | AUTHENTICATION_REQUIRED | CAPTCHA_BLOCKED | COMPLETE | FAILED\",\n" +
                "  \"reason\": \"short explanation\"\n" +
                "}\n";
    }

    /*
     * ================================================================
     * PAGE INSPECTION
     * ================================================================
     */

    private String buildPageState(
            Page page
    ) {

        try {

            String body =
                    page.locator("body")
                            .innerText();

            body =
                    truncate(
                            body,
                            MAX_PAGE_TEXT
                    );


            StringBuilder controls =
                    new StringBuilder();


            for (
                    Frame frame
                    : page.frames()
            ) {

                Locator locator =
                        frame.locator(
                                "input:visible, " +
                                        "textarea:visible, " +
                                        "select:visible, " +
                                        "button:visible, " +
                                        "[role='button']:visible"
                        );


                int count =
                        locator.count();


                for (
                        int i = 0;
                        i < count;
                        i++
                ) {

                    Locator control =
                            locator.nth(i);


                    controls
                            .append(
                                    describeControl(control)
                            )
                            .append("\n");
                }
            }


            return """
                    PAGE TEXT:
                    %s

                    VISIBLE CONTROLS:
                    %s
                    """.formatted(
                    body,
                    truncate(
                            controls.toString(),
                            MAX_PAGE_TEXT
                    )
            );

        } catch (Exception e) {

            return "Unable to inspect current page: "
                    + e.getMessage();
        }
    }


    private String describeControl(
            Locator control
    ) {

        return String.format(
                "tag=%s type=%s name=%s id=%s label=%s placeholder=%s aria=%s required=%s value=%s",
                safeTag(control),
                attr(control, "type"),
                attr(control, "name"),
                attr(control, "id"),
                findLabel(control),
                attr(control, "placeholder"),
                attr(control, "aria-label"),
                isRequired(control),
                attr(control, "value")
        );
    }


    /*
     * ================================================================
     * TARGET FINDING
     * ================================================================
     */

    private Locator findTarget(
            Page page,
            String target
    ) {

        if (
                target == null
                        || target.isBlank()
        ) {

            return null;
        }


        String normalized =
                normalize(target);


        /*
         * LABEL
         */

        for (
                Frame frame
                : page.frames()
        ) {

            Locator labels =
                    frame.locator(
                            "label:visible"
                    );


            for (
                    int i = 0;
                    i < labels.count();
                    i++
            ) {

                Locator label =
                        labels.nth(i);


                String text =
                        normalize(
                                safeText(label)
                        );


                if (
                        text.contains(normalized)
                                || normalized.contains(text)
                ) {

                    String forId =
                            attr(
                                    label,
                                    "for"
                            );


                    if (
                            !forId.isBlank()
                    ) {

                        Locator control =
                                frame.locator(
                                        "#" + escapeCss(forId)
                                );


                        if (
                                control.count() > 0
                        ) {

                            return control.first();
                        }
                    }


                    Locator nested =
                            label.locator(
                                    "input, textarea, select"
                            );


                    if (
                            nested.count() > 0
                    ) {

                        return nested.first();
                    }
                }
            }
        }


        /*
         * INPUT / TEXTAREA / SELECT
         */

        for (
                Frame frame
                : page.frames()
        ) {

            Locator controls =
                    frame.locator(
                            "input:visible, textarea:visible, select:visible"
                    );


            for (
                    int i = 0;
                    i < controls.count();
                    i++
            ) {

                Locator control =
                        controls.nth(i);


                String haystack =
                        normalize(
                                attr(control, "name")
                                        + " "
                                        + attr(control, "id")
                                        + " "
                                        + attr(control, "placeholder")
                                        + " "
                                        + attr(control, "aria-label")
                                        + " "
                                        + findLabel(control)
                        );


                if (
                        haystack.contains(normalized)
                                || normalized.contains(haystack)
                ) {

                    return control;
                }
            }
        }


        return null;
    }


    private Locator findClickable(
            Page page,
            String target
    ) {

        String normalized =
                normalize(target);


        for (
                Frame frame
                : page.frames()
        ) {

            Locator buttons =
                    frame.locator(
                            "button:visible, " +
                                    "input[type='submit']:visible, " +
                                    "[role='button']:visible, " +
                                    "a:visible"
                    );


            for (
                    int i = 0;
                    i < buttons.count();
                    i++
            ) {

                Locator button =
                        buttons.nth(i);


                String text =
                        normalize(
                                safeText(button)
                                        + " "
                                        + attr(
                                        button,
                                        "aria-label"
                                )
                                        + " "
                                        + attr(
                                        button,
                                        "title"
                                )
                        );


                if (
                        text.contains(normalized)
                                || normalized.contains(text)
                ) {

                    return button;
                }
            }
        }


        return null;
    }


    private Locator findFileInput(
            Page page,
            String target
    ) {

        for (
                Frame frame
                : page.frames()
        ) {

            Locator files =
                    frame.locator(
                            "input[type='file']"
                    );


            if (
                    files.count() == 1
            ) {

                return files.first();
            }
        }


        if (
                target != null
                        && !target.isBlank()
        ) {

            return findTarget(
                    page,
                    target
            );
        }


        return null;
    }


    /*
     * ================================================================
     * SAFE PROFILE
     * ================================================================
     */

    private boolean isAllowedProfileField(
            String field
    ) {

        return switch (field) {

            case "FIRST_NAME",
                 "LAST_NAME",
                 "EMAIL",
                 "PHONE",
                 "LOCATION",
                 "LINKEDIN",
                 "GITHUB",
                 "PORTFOLIO",
                 "PROFESSIONAL_TITLE" -> true;

            default -> false;
        };
    }


    private String profileValue(
            User user,
            String field
    ) {

        return switch (field) {

            case "FIRST_NAME" ->
                    firstName(
                            user.getName()
                    );

            case "LAST_NAME" ->
                    lastName(
                            user.getName()
                    );

            case "EMAIL" ->
                    safe(
                            user.getEmail()
                    );

            case "PHONE" ->
                    safe(
                            user.getPhone()
                    );

            case "LOCATION" ->
                    safe(
                            user.getLocation()
                    );

            case "LINKEDIN" ->
                    safe(
                            user.getLinkedinUrl()
                    );

            case "GITHUB" ->
                    safe(
                            user.getGithubUrl()
                    );

            case "PORTFOLIO" ->
                    safe(
                            user.getPortfolioUrl()
                    );

            case "PROFESSIONAL_TITLE" ->
                    safe(
                            user.getProfessionalTitle()
                    );

            default ->
                    "";
        };
    }


    /*
     * ================================================================
     * TEMPORARY ANSWER SAFETY
     * ================================================================
     */

    private boolean isSafeTemporaryTextControl(
            Locator locator
    ) {

        String type =
                attr(
                        locator,
                        "type"
                ).toLowerCase(Locale.ROOT);


        String tag =
                safeTag(locator);


        if (
                !"input".equals(tag)
                        && !"textarea".equals(tag)
        ) {

            return false;
        }


        return !(
                "radio".equals(type)
                        || "checkbox".equals(type)
                        || "file".equals(type)
                        || "date".equals(type)
                        || "number".equals(type)
                        || "password".equals(type)
        );
    }


    private boolean isSensitiveQuestion(
            String question
    ) {

        String q =
                normalize(question);


        String[] sensitiveTerms = {

                "work authorization",
                "legally authorized",
                "authorized to work",
                "visa",
                "sponsorship",
                "sponsor",
                "citizenship",
                "citizen",
                "nationality",
                "military",
                "veteran",
                "disability",
                "disabled",
                "gender",
                "race",
                "ethnicity",
                "religion",
                "criminal",
                "conviction",
                "security clearance",
                "government clearance",
                "export control",
                "legal",
                "immigration",
                "age",
                "date of birth",
                "birth date"
        };


        for (
                String term
                : sensitiveTerms
        ) {

            if (
                    q.contains(term)
            ) {

                return true;
            }
        }


        return false;
    }


    /*
     * ================================================================
     * SUBMIT PROTECTION
     * ================================================================
     */

    private boolean isSubmitAction(
            String target
    ) {

        String t =
                normalize(target);


        return t.contains("submit application")
                || t.equals("submit")
                || t.contains("submit application")
                || t.contains("send application")
                || t.contains("apply now");
    }


    private boolean hasPendingRequiredQuestions(
            User user
    ) {

        return applicationProfileService
                .getMissingDetails(user)
                .stream()
                .anyMatch(
                        ApplicationProfileService.MissingField::required
                );
    }


    private boolean hasVisibleRequiredEmptyField(
            Page page
    ) {

        for (
                Frame frame
                : page.frames()
        ) {

            Locator controls =
                    frame.locator(
                            "input:visible, textarea:visible, select:visible"
                    );


            for (
                    int i = 0;
                    i < controls.count();
                    i++
            ) {

                Locator control =
                        controls.nth(i);


                if (
                        !isRequired(control)
                ) {

                    continue;
                }


                String type =
                        attr(
                                control,
                                "type"
                        ).toLowerCase(
                                Locale.ROOT
                        );


                if (
                        "checkbox".equals(type)
                                || "radio".equals(type)
                ) {

                    if (
                            !isChecked(control)
                    ) {

                        return true;
                    }

                    continue;
                }


                String value =
                        attr(
                                control,
                                "value"
                        );


                if (
                        value.isBlank()
                ) {

                    return true;
                }
            }
        }


        return false;
    }


    /*
     * ================================================================
     * SUBMISSION DETECTION
     * ================================================================
     */

    private boolean looksLikeSuccessfulSubmission(
            Page page
    ) {

        try {

            String body =
                    page.locator("body")
                            .innerText()
                            .toLowerCase(
                                    Locale.ROOT
                            );


            return body.contains(
                    "application submitted"
            )
                    || body.contains(
                    "thank you for applying"
            )
                    || body.contains(
                    "thanks for applying"
            )
                    || body.contains(
                    "your application has been submitted"
            )
                    || body.contains(
                    "application received"
            );

        } catch (Exception e) {

            return false;
        }
    }


    /*
     * ================================================================
     * CAPTCHA / AUTH
     * ================================================================
     */

    private boolean hasHumanVerification(
            Page page
    ) {

        try {

            if (
                    page.locator(
                            ".g-recaptcha:visible"
                    ).count() > 0
            ) {

                return true;
            }


            if (
                    page.locator(
                            "iframe[src*='captcha']:visible"
                    ).count() > 0
            ) {

                return true;
            }


            String text =
                    page.locator("body")
                            .innerText()
                            .toLowerCase(
                                    Locale.ROOT
                            );


            return text.contains(
                    "verify you are human"
            )
                    || text.contains(
                    "i'm not a robot"
            )
                    || text.contains(
                    "human verification"
            )
                    || text.contains(
                    "unusual traffic"
            );

        } catch (Exception e) {

            return false;
        }
    }


    private boolean authenticationRequired(
            Page page
    ) {

        String text =
                safeBody(page)
                        .toLowerCase(
                                Locale.ROOT
                        );


        return (
                text.contains(
                        "sign in to continue"
                )
                        || text.contains(
                        "sign in with google"
                )
                        || text.contains(
                        "google account"
                )
                        || text.contains(
                        "authentication required"
                )
        );
    }


    /*
     * ================================================================
     * CONTROL HELPERS
     * ================================================================
     */

    private void fillNormalControl(
            Locator locator,
            String value
    ) {

        String tag =
                safeTag(locator);


        if (
                "select".equals(tag)
        ) {

            try {

                locator.selectOption(
                        value
                );

                return;

            } catch (Exception ignored) {
            }
        }


        locator.fill(value);
    }


    private boolean isRequired(
            Locator locator
    ) {

        String required =
                attr(
                        locator,
                        "required"
                );


        if (
                !required.isBlank()
        ) {

            return true;
        }


        return "true".equalsIgnoreCase(
                attr(
                        locator,
                        "aria-required"
                )
        );
    }


    private boolean isChecked(
            Locator locator
    ) {

        try {

            return locator.isChecked();

        } catch (Exception e) {

            return false;
        }
    }


    private String findLabel(
            Locator locator
    ) {

        try {

            Locator parent =
                    locator.locator(
                            "xpath=ancestor::label[1]"
                    );


            if (
                    parent.count() > 0
            ) {

                return safeText(
                        parent.first()
                );
            }


            String id =
                    attr(
                            locator,
                            "id"
                    );


            if (
                    !id.isBlank()
            ) {

                Locator label =
                        locator.page()
                                .locator(
                                        "label[for='"
                                                + escapeCss(id)
                                                + "']"
                                );


                if (
                        label.count() > 0
                ) {

                    return safeText(
                            label.first()
                    );
                }
            }

        } catch (Exception ignored) {
        }


        return "";
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


    /*
     * ================================================================
     * JSON PARSING
     * ================================================================
     */

    private AgentAction parseAction(
            String raw
    ) {

        try {

            String json =
                    raw == null
                            ? ""
                            : raw.trim();


            if (
                    json.startsWith("```")
            ) {

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


            JsonNode node =
                    objectMapper.readTree(
                            json
                    );


            if (
                    node == null
                            || !node.isObject()
            ) {

                return null;
            }


            return new AgentAction(
                    text(
                            node,
                            "action"
                    ),
                    text(
                            node,
                            "field"
                    ),
                    text(
                            node,
                            "target"
                    ),
                    text(
                            node,
                            "status"
                    ),
                    text(
                            node,
                            "reason"
                    ),
                    text(
                            node,
                            "value"
                    ),
                    node.get("questions")
            );

        } catch (Exception e) {

            System.out.println(
                    "Gemini JSON parsing error: "
                            + e.getMessage()
            );

            return null;
        }
    }


    private String text(
            JsonNode node,
            String field
    ) {

        JsonNode value =
                node.get(field);


        return value == null
                || value.isNull()
                ? ""
                : value.asText(
                ""
        ).trim();
    }


    /*
     * ================================================================
     * UTILITY
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


    private String safeBody(
            Page page
    ) {

        try {

            return truncate(
                    page.locator("body")
                            .innerText(),
                    10000
            );

        } catch (Exception e) {

            return "";
        }
    }


    private String truncate(
            String value,
            int max
    ) {

        if (
                value == null
        ) {

            return "";
        }


        if (
                value.length() <= max
        ) {

            return value;
        }


        return value.substring(
                0,
                max
        )
                + "\n...[TRUNCATED]";
    }


    private String normalize(
            String value
    ) {

        return safe(value)
                .toLowerCase(
                        Locale.ROOT
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .trim();
    }

    private long requestedWaitMillis(String value) {
        try {
            String requested = safe(value).toLowerCase(Locale.ROOT).trim();
            long wait = Long.parseLong(requested.replaceAll("[^0-9]", ""));
            if (!requested.contains("ms") && requested.matches(".*(?:s|sec|second).*")) {
                wait *= 1000L;
            }
            return Math.max(0L, Math.min(wait, 60_000L));
        } catch (NumberFormatException ignored) {
            return 1500L;
        }
    }


    private String safe(
            String value
    ) {

        return value == null
                ? ""
                : value.trim();
    }


    private String firstName(
            String name
    ) {

        if (
                name == null
                        || name.isBlank()
        ) {

            return "";
        }


        String[] parts = name.trim().split("\\s+");
        return parts.length <= 1 ? parts[0] : String.join(" ", java.util.Arrays.copyOf(parts, parts.length - 1));
    }


    private String lastName(
            String name
    ) {

        if (
                name == null
                        || name.isBlank()
        ) {

            return "";
        }


        String[] parts =
                name.trim()
                        .split("\\s+");


        return parts.length <= 1
                ? ""
                : parts[
                parts.length - 1
                ];
    }


    private String escapeCss(
            String value
    ) {

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


    private String messageOrDefault(
            String message,
            String fallback
    ) {

        return message == null
                || message.isBlank()
                ? fallback
                : message;
    }


    private void appendActionFeedback(
            StringBuilder feedback,
            String message
    ) {
        if (feedback == null) {
            return;
        }

        feedback.setLength(0);
        feedback.append(message == null ? "Browser action failed." : message);
    }


    /*
     * ================================================================
     * RESULT
     * ================================================================
     */

    public record AgentAction(
            String action,
            String field,
            String target,
            String status,
            String reason,
            String value,
            JsonNode questions
    ) {}


    public record Result(
            boolean success,
            boolean blocked,
            boolean authenticationRequired,
            boolean userInputRequired,
            String message,
            String submittedUrl
    ) {

        public static Result success(
                String message,
                String submittedUrl
        ) {

            return new Result(
                    true,
                    false,
                    false,
                    false,
                    message,
                    submittedUrl
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
                    null
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
                    null
            );
        }


        public static Result authenticationRequired(
                String message
        ) {

            return new Result(
                    false,
                    false,
                    true,
                    false,
                    message,
                    null
            );
        }


        public static Result userInputRequired(
                String message
        ) {

            return new Result(
                    false,
                    false,
                    false,
                    true,
                    message,
                    null
            );
        }
    }
}
