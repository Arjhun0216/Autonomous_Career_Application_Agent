package com.jobscraper.job_platform.application.automation;

import com.jobscraper.job_platform.application.ApplicationQuestion;
import com.jobscraper.job_platform.application.ApplicationQuestionRepository;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Deterministic Zoho form flow. This deliberately does not call an LLM. */
@Service
@RequiredArgsConstructor
public class ZohoFormAutomationService {
    private static final long ACTION_INTERVAL_MS = 3_000L;
    private final ApplicationQuestionRepository questionRepository;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    public GeminiApplicationAgent.Result apply(Page page, User user, String resumePath, Long applicationId) {
        try {
            Path resume = Path.of(resumePath).toAbsolutePath().normalize();
            if (!Files.isRegularFile(resume)) return GeminiApplicationAgent.Result.failed("Generated resume file is missing.");

            Locator upload = null;
            for (Frame frame : page.frames()) {
                Locator files = frame.locator("input[type='file']");
                if (files.count() > 0) { upload = files.first(); break; }
            }
            if (upload == null) return GeminiApplicationAgent.Result.failed("Zoho resume upload control was not found.");
            upload.setInputFiles(resume);
            if (!waitForResumeParsing(page, 600_000L)) {
                return GeminiApplicationAgent.Result.failed("Zoho resume parsing did not finish within ten minutes. The form was not submitted.");
            }

            Map<String, String> trusted = trustedAnswers(user);
            List<ApplicationQuestion> pending = new ArrayList<>();
            List<ApplicationQuestion> savedAnswers = questionRepository.findByApplicationIdOrderByQuestionIdAsc(applicationId);
            for (int pageNo = 0; pageNo < 8; pageNo++) {
                int visibleControls = fillVisibleFields(page, user, trusted, applicationId, pending, savedAnswers);
                if (visibleControls == 0) {
                    return GeminiApplicationAgent.Result.failed("No visible Zoho application fields were found after resume parsing. The form was not submitted.");
                }
                if (pageNo == 0 && recognizedProfileFieldCount(page, trusted) == 0) {
                    return GeminiApplicationAgent.Result.failed("Zoho form fields were visible but none of the requested profile fields could be identified. The form was not submitted.");
                }

                Locator submit = findAction(page, "submit application", "submit");
                if (submit != null) {
                    if (!pending.isEmpty()) {
                        return GeminiApplicationAgent.Result.userInputRequired(
                                "Zoho needs answers saved in Pending Approvals before this application can be submitted.");
                    }
                    submit.click();
                    page.waitForTimeout(5_000L);
                    String submissionError = findSubmissionError(page);
                    if (submissionError != null) {
                        return GeminiApplicationAgent.Result.failed(
                                "Zoho did not accept the application: " + submissionError
                        );
                    }
                    System.out.println("Zoho application submitted successfully: no form errors were visible after 5 seconds.");
                    return GeminiApplicationAgent.Result.success(
                            "Zoho application submitted successfully.", page.url());
                }
                Locator next = findAction(page, "next", "continue", "save and continue");
                if (next == null) {
                    if (!pending.isEmpty()) return GeminiApplicationAgent.Result.userInputRequired(
                            "Zoho needs answers saved in Pending Approvals before this application can be submitted.");
                    return GeminiApplicationAgent.Result.failed("Zoho form has no visible Submit, Next, or Continue action.");
                }
                next.click();
                page.waitForTimeout(ACTION_INTERVAL_MS);
            }

            return GeminiApplicationAgent.Result.failed("Zoho form exceeded the supported number of pages without reaching Submit.");
        } catch (Exception e) {
            return GeminiApplicationAgent.Result.failed("Zoho automation failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    /** Looks for visible Zoho validation and submission errors after final submit. */
    private String findSubmissionError(Page page) {
        for (Frame frame : page.frames()) {
            try {
                Locator alerts = frame.locator(
                        "[role='alert']:visible, .lyteFieldError:visible, .error-message:visible, " +
                                ".field-error:visible, .errorMsg:visible, .error-message-container:visible"
                );
                for (int i = 0; i < Math.min(alerts.count(), 30); i++) {
                    Locator alert = alerts.nth(i);
                    String text = safe(alert.innerText());
                    String classes = safe(alert.getAttribute("class")).toLowerCase(Locale.ROOT);
                    boolean explicitError = (classes.contains("error") || classes.contains("invalid"))
                            && !classes.contains("success");
                    if (!text.isBlank() && (explicitError || isSubmissionErrorText(text))) {
                        return conciseError(text);
                    }
                }

                Locator invalidFields = frame.locator(
                        "input:invalid:visible, textarea:invalid:visible, select:invalid:visible, [aria-invalid='true']:visible"
                );
                if (invalidFields.count() > 0) {
                    String label = safe(fieldLabel(invalidFields.first()));
                    return label.isBlank()
                            ? "One or more required fields are invalid."
                            : "The field '" + conciseError(label) + "' is invalid.";
                }

                String body = safe(frame.locator("body").innerText());
                String matchingLine = body.lines()
                        .map(String::trim)
                        .filter(this::isSubmissionErrorText)
                        .findFirst()
                        .orElse("");
                if (!matchingLine.isBlank()) return conciseError(matchingLine);
            } catch (Exception ignored) {
                // Zoho can replace its embedded form frame during submission;
                // inspect remaining live frames and the main page instead.
            }
        }
        return null;
    }

    private boolean isSubmissionErrorText(String text) {
        String normalized = normalize(text);
        return normalized.contains("please correct the errors")
                || normalized.contains("please fix the errors")
                || normalized.contains("there was an error")
                || normalized.contains("an error occurred")
                || normalized.contains("error occurred while")
                || normalized.contains("submission failed")
                || normalized.contains("failed to submit")
                || normalized.contains("unable to submit")
                || normalized.contains("application could not be submitted")
                || normalized.contains("invalid email")
                || normalized.contains("enter a valid")
                || normalized.contains("is required")
                || normalized.contains("is mandatory");
    }

    private String conciseError(String message) {
        String clean = safe(message).replaceAll("\\s+", " ").trim();
        return clean.length() > 300 ? clean.substring(0, 297) + "..." : clean;
    }

    private int recognizedProfileFieldCount(Page page, Map<String, String> answers) {
        int found = 0;
        for (Frame frame : page.frames()) {
            Locator controls = frame.locator("input:not([type='hidden']):not([type='file']), textarea, select, [role='combobox'], lyte-dropdown");
            for (int i = 0; i < Math.min(controls.count(), 250); i++) {
                Locator control = controls.nth(i);
                try { if (control.isVisible() && answerFor(fieldLabel(control), answers, control) != null) found++; }
                catch (Exception ignored) { }
            }
        }
        return found;
    }

    private int fillVisibleFields(Page page, User user, Map<String, String> answers, Long applicationId,
                                   List<ApplicationQuestion> pending, List<ApplicationQuestion> savedAnswers) {
        int visibleControls = 0;
        int frameIndex = 0;
        for (Frame frame : page.frames()) {
            Locator controls = frame.locator("input:not([type='hidden']):not([type='file']), textarea, select, [role='combobox'], lyte-dropdown");
            int count = Math.min(controls.count(), 250);
            for (int i = 0; i < count; i++) {
                Locator control = controls.nth(i);
                try {
                    if (!control.isVisible() || !control.isEnabled()) continue;
                    visibleControls++;
                    String label = fieldLabel(control).trim();
                    if (label.isBlank()) {
                        if (!isRequired(control, label)) continue;
                        label = "Unlabeled required application field " + (i + 1);
                    }
                    // Zoho parses skills from the uploaded resume; do not overwrite or ask for them.
                    if (hasPhrase(label, "skill set") || hasPhrase(label, "skills")) continue;
                    // The country selector shares the Contact label in Zoho. Keep its default (+91)
                    // and fill only the adjacent phone number input from the profile.
                    if (isPhoneCountryCodeControl(label, control)) continue;
                    // Zoho renders the honorific dropdown and given-name input under the same
                    // "First Name" label. Never reuse the text answer for the dropdown.
                    String saved = isTitleDropdown(label, control) ? "" : savedAnswer(control, label, savedAnswers);
                    if (saved.isBlank() && !isTitleDropdown(label, control)) saved = savedProfileAnswer(user, control, label);
                    String answer = saved.isBlank() ? answerFor(label, answers, control) : saved;
                    if (answer == null && isUserQuestion(label) && saved.isBlank()) {
                        fillTemporaryText(control, label);
                        recordPending(user, applicationId, label, control, frameIndex, pending);
                        continue;
                    }
                    if (answer != null) {
                        boolean alreadyCorrect = isCurrentValue(control, answer);
                        boolean filled = alreadyCorrect || fillControl(page, control, answer);
                        if (filled) {
                            resolvePending(user, applicationId, label, control, answer);
                            page.waitForTimeout(50L);
                        }
                        else recordPending(user, applicationId, label, control, frameIndex, pending);
                    } else if (isAlreadyFilled(control)) {
                        continue;
                    } else {
                        fillTemporaryText(control, label);
                        recordPending(user, applicationId, label, control, frameIndex, pending);
                    }
                } catch (Exception ignored) { }
            }
            frameIndex++;
        }
        return visibleControls;
    }

    private boolean waitForResumeParsing(Page page, long maxWaitMillis) {
        long deadline = System.currentTimeMillis() + maxWaitMillis;
        boolean sawProcessing = false;
        int stableReadyChecks = 0;
        while (System.currentTimeMillis() < deadline) {
            page.waitForTimeout(5_000L);
            String text = "";
            try { text = safe(page.locator("body").innerText()).toLowerCase(Locale.ROOT); } catch (Exception ignored) { }
            boolean processing = text.contains("parsing resume") || text.contains("processing resume")
                    || text.contains("extracting information") || text.contains("reading your resume")
                    || text.contains("uploading resume") || text.contains("please wait");
            boolean failed = text.contains("resume parsing failed") || text.contains("failed to parse")
                    || text.contains("invalid resume") || text.contains("upload failed");
            if (failed) return false;
            if (processing) {
                sawProcessing = true;
                stableReadyChecks = 0;
                continue;
            }
            if (sawProcessing || text.contains("resume parsed") || text.contains("parsing completed")
                    || text.contains("review the extracted details")) return true;
            if (visibleFieldCount(page) > 2) {
                // Some Zoho forms parse silently. Require six consecutive polls
                // (30 seconds) with the application form rendered before proceeding.
                if (++stableReadyChecks >= 6) return true;
            } else {
                stableReadyChecks = 0;
            }
        }
        return false;
    }

    private int visibleFieldCount(Page page) {
        int count = 0;
        for (Frame frame : page.frames()) {
            try { count += Math.min(frame.locator("input:not([type='hidden']):not([type='file']):visible, textarea:visible, select:visible, [role='combobox']:visible, lyte-dropdown:visible").count(), 250); }
            catch (Exception ignored) { }
        }
        return count;
    }

    private boolean isCurrentValue(Locator control, String expected) {
        try {
            String tag = safe(String.valueOf(control.evaluate("e=>e.tagName.toLowerCase()")));
            String value = tag.equals("select") ? safe(control.locator("option:checked").innerText())
                    : safe(String.valueOf(control.evaluate("e=>e.value||e.getAttribute('aria-valuetext')||''")));
            return !value.isBlank() && normalize(value).equals(normalize(expected));
        } catch (Exception e) { return false; }
    }

    private Map<String, String> trustedAnswers(User user) {
        String fullName = safe(user.getName()).trim();
        String[] parts = fullName.isBlank() ? new String[0] : fullName.split("\\s+");
        String first = parts.length <= 1 ? (parts.length == 0 ? "" : parts[0])
                : String.join(" ", java.util.Arrays.copyOf(parts, parts.length - 1));
        String last = parts.length <= 1 ? "" : parts[parts.length - 1];
        String phone = safe(user.getPhone()).replaceFirst("^\\+?91\\s*", "").trim();
        String gender = safe(user.getGender());
        String honorific = gender.equalsIgnoreCase("Male") ? "Mr" : gender.equalsIgnoreCase("Female") ? "Ms" : "";
        String experience = user.getUserExperience() == null ? "" : switch (user.getUserExperience()) {
            case 0 -> "Fresher";
            case 2 -> "2-3";
            case 3 -> "3-4";
            default -> "";
        };
        String currentEmployer = additionalDetail(user, "current employer", "current employer name", "current company", "employer");
        String currentCtc = additionalDetail(user, "current ctc", "current salary", "present ctc");
        String expectedCtc = additionalDetail(user, "expected ctc", "expected salary");
        String noticePeriod = additionalDetail(user, "notice period", "availability", "joining period");
        return Map.ofEntries(
                Map.entry("first name", first), Map.entry("last name", last),
                Map.entry("email", safe(user.getEmail())), Map.entry("phone", phone),
                Map.entry("contact", phone), Map.entry("mobile", phone),
                Map.entry("gender", gender), Map.entry("title", honorific),
                Map.entry("salutation", honorific), Map.entry("graduation year", extractEndYear(user.getEducation())),
                Map.entry("year of graduation", extractEndYear(user.getEducation())),
                Map.entry("experience", experience), Map.entry("current location", safe(user.getLocation())),
                Map.entry("location", safe(user.getLocation())), Map.entry("city", safe(user.getLocation())),
                Map.entry("current employer", currentEmployer), Map.entry("current company", currentEmployer),
                Map.entry("current ctc", currentCtc), Map.entry("current salary", currentCtc),
                Map.entry("expected ctc", expectedCtc), Map.entry("expected salary", expectedCtc),
                Map.entry("notice period", noticePeriod)
        );
    }

    private String answerFor(String label, Map<String, String> answers, Locator control) {
        String normalized = normalize(label);
        if (isPhoneCountryCodeControl(label, control)) return null;
        if (normalized.contains("heard about") || normalized.contains("how did you hear")
                || normalized.contains("how did you come across")) return "Zoho Careers Page";
        if (isTitleDropdown(label, control)) {
            return answers.getOrDefault("title", "");
        }
        for (Map.Entry<String, String> entry : answers.entrySet()) {
            if (!entry.getValue().isBlank() && hasPhrase(normalized, entry.getKey())) return entry.getValue();
        }
        if (isUserQuestion(label)) return null;
        return null;
    }

    private boolean isTitleDropdown(String label, Locator control) {
        String normalized = normalize(label);
        return isDropdown(control) && (normalized.equals("first name") || normalized.startsWith("first name "));
    }

    /** Reuse answers from users.other_details across application attempts. */
    private String savedProfileAnswer(User user, Locator control, String label) {
        if (user == null || user.getOtherDetails() == null || user.getOtherDetails().isBlank()) return "";
        String wanted = normalize(label);
        if (wanted.isBlank()) return "";
        try {
            JsonNode profile = objectMapper.readTree(user.getOtherDetails()).path("application_profile");
            JsonNode pending = profile.path("pending_questions");
            if (pending.isObject()) {
                var entries = pending.properties().iterator();
                while (entries.hasNext()) {
                    JsonNode item = entries.next().getValue();
                    String savedQuestion = normalize(item.path("question").asText(""));
                    String answer = safe(item.path("answer").asText(""));
                    String status = safe(item.path("status").asText(""));
                    if (!answer.isBlank() && (status.isBlank() || "ANSWERED".equalsIgnoreCase(status)
                            || "RESOLVED".equalsIgnoreCase(status)) && wanted.equals(savedQuestion)) return answer;
                }
            }
            JsonNode answers = profile.path("answers");
            if (answers.isObject()) {
                var entries = answers.properties().iterator();
                while (entries.hasNext()) {
                    var entry = entries.next();
                    String key = normalize(entry.getKey());
                    String answer = entry.getValue().isValueNode() ? safe(entry.getValue().asText()) : "";
                    if (!answer.isBlank() && wanted.equals(key)) return answer;
                }
            }
        } catch (Exception ignored) { }
        return "";
    }

    private boolean isPhoneCountryCodeControl(String label, Locator control) {
        String n = normalize(label);
        if (!isDropdown(control)) return false;
        if (!(n.contains("phone") || n.contains("contact") || n.contains("mobile") || n.contains("country code") || n.contains("dial code"))) return false;
        try {
            String details = normalize(safe(control.innerText()) + " " + safe(control.getAttribute("aria-label"))
                    + " " + safe(control.getAttribute("value")));
            if (details.contains("+91") || details.contains("india")) return true;
            Locator options = control.locator("option");
            for (int i = 0; i < Math.min(options.count(), 30); i++) {
                String option = normalize(safe(options.nth(i).innerText()));
                if (option.contains("+91") || option.contains("india")) return true;
            }
            return n.contains("country code") || n.contains("dial code");
        } catch (Exception ignored) {
            return n.contains("country code") || n.contains("dial code");
        }
    }

    private String additionalDetail(User user, String... keys) {
        try {
            JsonNode root = objectMapper.readTree(safe(user.getOtherDetails()));
            List<String> normalizedKeys = java.util.Arrays.stream(keys).map(this::normalize).toList();
            return findAdditionalDetail(root, normalizedKeys);
        } catch (Exception ignored) { return ""; }
    }

    private String findAdditionalDetail(JsonNode node, List<String> keys) {
        if (node == null) return "";
        if (node.isObject()) {
            var fields = ((ObjectNode) node).properties().iterator();
            while (fields.hasNext()) {
                var field = fields.next();
                String fieldKey = normalize(field.getKey());
                if (keys.stream().anyMatch(k -> fieldKey.equals(k)
                        || fieldKey.startsWith(k + " ")
                        || fieldKey.equals(k.replace(" ", ""))
                        || fieldKey.startsWith(k.replace(" ", "") + " "))) {
                    JsonNode value = field.getValue();
                    if (value.isValueNode() && !value.asText().isBlank()) return value.asText().trim();
                }
                String nested = findAdditionalDetail(field.getValue(), keys);
                if (!nested.isBlank()) return nested;
            }
        } else if (node.isArray()) {
            for (JsonNode item : node) {
                String nested = findAdditionalDetail(item, keys);
                if (!nested.isBlank()) return nested;
            }
        }
        return "";
    }

    private boolean isDropdown(Locator control) {
        try {
            String tag = safe(String.valueOf(control.evaluate("e=>e.tagName.toLowerCase()")));
            return tag.equals("select") || tag.contains("dropdown") || "combobox".equals(safe(control.getAttribute("role")));
        } catch (Exception e) { return false; }
    }

    private boolean fillControl(Page page, Locator control, String value) {
        if (value == null || value.isBlank()) return false;
        String tag = safe(String.valueOf(control.evaluate("e=>e.tagName.toLowerCase()")));
        String type = safe(control.getAttribute("type")).toLowerCase(Locale.ROOT);
        if (type.equals("radio") || type.equals("checkbox") || type.equals("file")) return false;
        if (tag.equals("select")) {
            Locator options = control.locator("option");
            for (int i=0;i<options.count();i++) {
                String option = safe(options.nth(i).innerText()).trim();
                if (optionMatches(option, value)) {
                    String optionValue = safe(options.nth(i).getAttribute("value"));
                    if (!optionValue.isBlank()) {
                        control.selectOption(optionValue);
                        return true;
                    }
                }
            }
            return false;
        }
        if (tag.contains("dropdown") || "combobox".equals(safe(control.getAttribute("role")))) {
            control.click();
            page.waitForTimeout(100L);
            for (Frame frame : page.frames()) {
                Locator options = frame.locator("[role='option']:visible, lyte-drop-item:visible, [id^='Lyte_Drop_Item']:visible");
                for (int i=0;i<options.count();i++) {
                    Locator option = options.nth(i);
                    if (optionMatches(safe(option.innerText()), value)) { option.click(); return true; }
                }
            }
            return false;
        }
        control.fill(value);
        return true;
    }

    private void recordPending(User user, Long applicationId, String label, Locator control, int frameIndex, List<ApplicationQuestion> pending) {
        if (applicationId == null || label.isBlank()) return;
        String fieldId = firstNonBlank(safe(control.getAttribute("id")), safe(control.getAttribute("name")), "frame" + frameIndex + "_" + normalize(label).replace(' ', '_'));
        List<ApplicationQuestion> records = questionRepository.findByApplicationIdOrderByQuestionIdAsc(applicationId);
        ApplicationQuestion existing = records.stream().filter(q -> fieldId.equalsIgnoreCase(safe(q.getFieldId()))
                || label.equalsIgnoreCase(safe(q.getQuestion()))).findFirst().orElse(null);
        if (existing == null) {
            String availableOptions = controlOptionJson(control);
            if (availableOptions.isBlank()) availableOptions = defaultOptionsJson(label);
            existing = questionRepository.save(ApplicationQuestion.builder().applicationId(applicationId)
                    .question(label).fieldId(fieldId).questionType(safe(control.getAttribute("type")))
                    .temporaryAnswer(shouldUseTemporaryText(control, label) ? "PENDING_USER_INPUT" : "")
                    .availableOptions(availableOptions).status("PENDING").required(true).createdAt(LocalDateTime.now()).build());
        } else if ("PENDING".equalsIgnoreCase(safe(existing.getStatus()))
                && safe(existing.getTemporaryAnswer()).isBlank()
                && shouldUseTemporaryText(control, label)) {
            existing.setTemporaryAnswer("PENDING_USER_INPUT");
            questionRepository.save(existing);
        }
        if ("PENDING".equalsIgnoreCase(safe(existing.getStatus()))) {
            pending.add(existing);
            persistPendingQuestion(user, existing, label, fieldId);
        }
    }

    /** A field that was previously queued must stop appearing in Pending Approvals once profile data fills it. */
    private void resolvePending(User user, Long applicationId, String label, Locator control, String answer) {
        if (applicationId == null) return;
        String id = firstNonBlank(safe(control.getAttribute("id")), safe(control.getAttribute("name")));
        List<ApplicationQuestion> records = questionRepository.findByApplicationIdOrderByQuestionIdAsc(applicationId);
        for (ApplicationQuestion question : records) {
            boolean sameField = (!id.isBlank() && id.equalsIgnoreCase(safe(question.getFieldId())))
                    || label.equalsIgnoreCase(safe(question.getQuestion()));
            if (sameField && "PENDING".equalsIgnoreCase(safe(question.getStatus()))) {
                question.setFinalAnswer(answer);
                question.setStatus("RESOLVED");
                questionRepository.save(question);
            }
        }
        try {
            JsonNode root = user.getOtherDetails() == null || user.getOtherDetails().isBlank()
                    ? objectMapper.createObjectNode() : objectMapper.readTree(user.getOtherDetails());
            if (!root.isObject()) return;
            JsonNode app = root.path("application_profile");
            JsonNode oldPending = app.path("pending_questions");
            if (!oldPending.isObject()) return;
            ObjectNode pending = (ObjectNode) oldPending;
            var entries = pending.properties().iterator();
            while (entries.hasNext()) {
                var entry = entries.next();
                JsonNode item = entry.getValue();
                String savedId = safe(item.path("fieldId").asText(""));
                String savedLabel = safe(item.path("question").asText(""));
                if ((!id.isBlank() && id.equalsIgnoreCase(savedId))
                        || label.equalsIgnoreCase(savedLabel)) {
                    if (item instanceof ObjectNode savedQuestion) {
                        savedQuestion.put("answer", answer);
                        savedQuestion.put("status", "RESOLVED");
                    }
                }
            }
            ObjectNode other = (ObjectNode) root;
            user.setOtherDetails(objectMapper.writeValueAsString(other));
            userRepository.save(user);
        } catch (Exception ignored) { }
    }

    private void persistPendingQuestion(User user, ApplicationQuestion question, String label, String fieldId) {
        try {
            JsonNode root = user.getOtherDetails() == null || user.getOtherDetails().isBlank()
                    ? objectMapper.createObjectNode() : objectMapper.readTree(user.getOtherDetails());
            ObjectNode other = root != null && root.isObject() ? (ObjectNode) root : objectMapper.createObjectNode();
            JsonNode appNode = other.get("application_profile");
            ObjectNode appProfile = appNode != null && appNode.isObject() ? (ObjectNode) appNode : objectMapper.createObjectNode();
            other.set("application_profile", appProfile);
            JsonNode pendingNode = appProfile.get("pending_questions");
            ObjectNode pending = pendingNode != null && pendingNode.isObject() ? (ObjectNode) pendingNode : objectMapper.createObjectNode();
            appProfile.set("pending_questions", pending);
            ObjectNode item = objectMapper.createObjectNode();
            item.put("question", label);
            item.put("fieldId", fieldId);
            item.put("applicationId", question.getApplicationId());
            item.put("type", safe(question.getQuestionType()).isBlank() ? "other" : safe(question.getQuestionType()));
            item.put("required", question.isRequired());
            item.put("temporaryAnswer", safe(question.getTemporaryAnswer()));
            item.put("answer", "");
            item.put("status", safe(question.getStatus()).isBlank() ? "PENDING" : safe(question.getStatus()));
            try {
                JsonNode options = objectMapper.readTree(safe(question.getAvailableOptions()));
                if (options != null && options.isArray()) item.set("options", options);
            } catch (Exception ignored) { }
            pending.set(fieldId, item);
            user.setOtherDetails(objectMapper.writeValueAsString(other));
            userRepository.save(user);
        } catch (Exception ignored) { }
    }

    private void fillTemporaryText(Locator control, String label) {
        if (!shouldUseTemporaryText(control, label) || isAlreadyFilled(control)) return;
        try { control.fill("PENDING_USER_INPUT"); } catch (Exception ignored) { }
    }

    private boolean shouldUseTemporaryText(Locator control, String label) {
        String type = safe(control.getAttribute("type")).toLowerCase(Locale.ROOT);
        String tag = "";
        try { tag = safe(String.valueOf(control.evaluate("e=>e.tagName.toLowerCase()"))); } catch (Exception ignored) { }
        String n = normalize(label);
        if (!(tag.equals("textarea") || tag.equals("input") && (type.isBlank() || type.equals("text")))) return false;
        return !List.of("email", "phone", "mobile", "contact", "date", "number", "url", "authorization",
                        "citizenship", "sponsorship", "visa", "military", "disability", "gender", "password", "captcha")
                .stream().anyMatch(n::contains);
    }

    private String fieldLabel(Locator c) {
        return safe(String.valueOf(c.evaluate("""
                e=>{const n=s=>(s||'').replace(/\\s+/g,' ').trim();
                const useful=s=>{s=n(s);return s.length>0&&s.length<180&&!/^(none|null|undefined|n\\/?a|select|choose|please select|rec[_-]?form|inputid|question[_-]?\\d+)/i.test(s)};
                for(const a of ['aria-label','data-label','data-field-label','lt-prop-label','title']){const v=e.getAttribute(a);if(useful(v))return n(v)}
                const labelled=(e.getAttribute('aria-labelledby')||'').split(/\\s+/).map(id=>document.getElementById(id)).filter(Boolean).map(x=>n(x.innerText)).filter(useful).join(' ');if(labelled)return labelled;
                if(e.labels&&e.labels.length){const v=[...e.labels].map(x=>n(x.innerText)).filter(useful).join(' ');if(v)return v}
                let p=e;for(let i=0;p&&i<9;i++,p=p.parentElement){
                  for(const a of ['data-label','data-field-label','lt-prop-label','aria-label']){const v=p.getAttribute&&p.getAttribute(a);if(useful(v))return n(v)}
                  const labels=p.querySelectorAll?p.querySelectorAll('label,[class*=label],[class*=question],[class*=FieldName],[class*=field-name],[class*=FieldLabel]'):[];
                  for(const l of labels){const v=n(l.innerText||l.textContent);if(useful(v))return v}
                  const prev=e.previousElementSibling;if(prev&&useful(prev.innerText||prev.textContent))return n(prev.innerText||prev.textContent);
                }
                for(const a of ['placeholder','name']){const v=e.getAttribute(a);if(useful(v))return n(v)}
                const id=e.id||'';return useful(id)?n(id):'';}
                """)));
    }

    private String controlOptionJson(Locator control) {
        try {
            if (!safe(String.valueOf(control.evaluate("e=>e.tagName.toLowerCase()"))).equals("select")) return "";
            List<String> options = new ArrayList<>();
            Locator values = control.locator("option");
            for (int i=0;i<Math.min(values.count(),100);i++) {
                String option = safe(values.nth(i).innerText());
                if (!option.isBlank()) options.add(option);
            }
            return objectMapper.writeValueAsString(options);
        } catch (Exception ignored) { return ""; }
    }

    private String defaultOptionsJson(String label) {
        String n = normalize(label);
        List<String> options = null;
        if (n.contains("gender")) {
            options = List.of("Male", "Female");
        } else if (n.contains("salutation") || n.contains("title")) {
            options = List.of("Mr", "Mrs", "Ms");
        } else if (n.contains("experience")) {
            options = List.of("Fresher", "2-3", "3-4");
        } else if (n.contains("country code") || n.contains("dial code")) {
            options = List.of("+91");
        } else if (n.contains("heard about") || n.contains("how did you hear")) {
            options = List.of("Zoho Careers Page");
        } else if (n.contains("current ctc") || n.contains("present ctc") || n.contains("current salary")) {
            options = List.of("Not Applicable", "1-3", "3-5", "5-7", "7-9");
        } else if (n.contains("expected ctc") || n.contains("expected salary")) {
            options = List.of("Open", "1-3", "3-5", "5-7", "7-9");
        } else if (n.contains("notice period")) {
            options = List.of("Immediate", "Not Applicable", "1 Month", "2 Month", "3 Month");
        } else if (n.contains("preferred") && (n.contains("location") || n.contains("city"))) {
            options = List.of("Chennai", "Coimbatore", "Madurai", "Salem", "Tenkasi", "Tirunelveli");
        }
        try { return options == null ? "" : objectMapper.writeValueAsString(options); }
        catch (Exception ignored) { return ""; }
    }

    private String savedAnswer(Locator control, String label, List<ApplicationQuestion> questions) {
        String id = firstNonBlank(safe(control.getAttribute("id")), safe(control.getAttribute("name")));
        return questions.stream()
                .filter(q -> "ANSWERED".equalsIgnoreCase(safe(q.getStatus())) || "RESOLVED".equalsIgnoreCase(safe(q.getStatus())))
                .filter(q -> label.equalsIgnoreCase(safe(q.getQuestion()))
                        || (!id.isBlank() && id.equalsIgnoreCase(safe(q.getFieldId()))))
                .map(q -> safe(q.getFinalAnswer())).filter(v -> !v.isBlank()).findFirst().orElse("");
    }

    private boolean isRequired(Locator c, String label) {
        return "true".equalsIgnoreCase(safe(c.getAttribute("required")))
                || "true".equalsIgnoreCase(safe(c.getAttribute("aria-required"))) || label.contains("*");
    }
    private boolean isUserQuestion(String label) {
        return (hasPhrase(label, "employer") && !hasPhrase(label, "previous")) || hasPhrase(label, "current company")
                || hasPhrase(label, "current ctc") || hasPhrase(label, "present ctc") || hasPhrase(label, "current salary")
                || hasPhrase(label, "expected ctc") || hasPhrase(label, "expected salary") || hasPhrase(label, "notice period")
                || (hasPhrase(label, "preferred") && (hasPhrase(label, "location") || hasPhrase(label, "city")));
    }
    private boolean isAlreadyFilled(Locator c) {
        String type = safe(c.getAttribute("type"));
        if (type.equalsIgnoreCase("radio") || type.equalsIgnoreCase("checkbox")) {
            try { return c.isChecked(); } catch (Exception e) { return false; }
        }
        try { return !safe(String.valueOf(c.evaluate("e=>e.value||e.getAttribute('aria-valuetext')||''"))).isBlank(); }
        catch (Exception e) { return false; }
    }
    private Locator findAction(Page page, String... names) {
        for (Frame frame : page.frames()) {
            Locator actions = frame.locator("button:visible, a:visible, [role='button']:visible, input[type='submit']:visible, input[type='button']:visible");
            for (int i=0;i<actions.count();i++) {
                Locator action = actions.nth(i);
                String text = normalize(firstNonBlank(safe(action.innerText()), safe(action.getAttribute("value")), safe(action.getAttribute("aria-label"))));
                for (String name : names) if (text.equals(normalize(name))) return action;
            }
        }
        return null;
    }
    private String extractEndYear(String json) {
        try { JsonNode n = objectMapper.readTree(json); if (n.isArray()) for (JsonNode e : n) { String y=e.path("endYear").asText("").trim(); if(!y.isBlank())return y; } if(n.isObject())return n.path("endYear").asText("").trim(); }
        catch(Exception ignored) { }
        return "";
    }
    private String skillText(String json) {
        if (json == null || json.isBlank()) return "";
        try { JsonNode n=objectMapper.readTree(json); if(n.isArray()){List<String> v=new ArrayList<>();n.forEach(x->v.add(x.isTextual()?x.asText():x.path("skill").asText(x.toString())));return String.join(", ",v);} }
        catch(Exception ignored) { }
        return json;
    }
    private String firstNonBlank(String... values) { for(String v:values) if(v!=null&&!v.isBlank())return v; return ""; }
    private String normalize(String value) { return safe(value).replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9+]+", " ").trim(); }
    private boolean hasPhrase(String value, String phrase) {
        String normalized = normalize(value);
        String expected = normalize(phrase);
        return normalized.contains(expected) || normalized.replace(" ", "").contains(expected.replace(" ", ""));
    }
    private boolean optionMatches(String option, String expected) {
        String o = normalize(option), e = normalize(expected);
        return o.equals(e) || (e.matches("[0-9]+ [0-9]+") && o.startsWith(e + " "));
    }
    private String safe(String value) { return value == null ? "" : value.trim(); }
}
