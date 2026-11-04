package com.jobscraper.job_platform.application.automation;

import com.jobscraper.job_platform.application.ApplicationQuestion;
import com.jobscraper.job_platform.application.ApplicationQuestionRepository;
import com.jobscraper.job_platform.user.User;
import com.jobscraper.job_platform.user.UserRepository;
import com.microsoft.playwright.Dialog;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
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

/** Deterministic Keka form workflow. It does not use Gemini for form filling or CAPTCHA handling. */
@Service
@RequiredArgsConstructor
public class KekaFormAutomationService {
    private static final long ACTION_WAIT_MS = 3000L;
    private final ApplicationQuestionRepository questionRepository;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    public GeminiApplicationAgent.Result apply(Page page, User user, String resumePath, Long applicationId) {
        try {
            if (page == null || user == null) return GeminiApplicationAgent.Result.failed("Keka page or user profile is unavailable.");
            Path resume = Path.of(safe(resumePath)).toAbsolutePath().normalize();
            if (!Files.isRegularFile(resume)) return GeminiApplicationAgent.Result.failed("Generated resume file is missing.");

            page.onDialog(Dialog::accept);
            acceptCookieConsent(page);

            Page formPage = page;
            if (!hasResumeInput(formPage)) {
                Locator apply = findAction(formPage, "apply", "apply now", "apply for this job", "apply to this job");
                if (apply == null) return GeminiApplicationAgent.Result.failed("Keka Apply control was not found and the application form is not open.");
                apply.scrollIntoViewIfNeeded();
                apply.click();
                page.waitForTimeout(ACTION_WAIT_MS);
                formPage = latestPage(page);
                acceptCookieConsent(formPage);
            }

            Locator upload = findFileInput(formPage);
            if (upload == null) return GeminiApplicationAgent.Result.failed("Keka resume upload control was not found after opening Apply.");
            upload.setInputFiles(resume);
            formPage.waitForTimeout(5000L);

            List<ApplicationQuestion> savedQuestions = applicationId == null ? new ArrayList<>()
                    : questionRepository.findByApplicationIdOrderByQuestionIdAsc(applicationId);
            for (ApplicationQuestion question : savedQuestions) {
                if (isLastWorkingDate(safe(question.getQuestion()))
                        && "PENDING".equalsIgnoreCase(safe(question.getStatus()))) {
                    question.setStatus("RESOLVED");
                    question.setFinalAnswer("Skipped as requested");
                    question.setAnsweredAt(LocalDateTime.now());
                    questionRepository.save(question);
                    updatePendingProfile(user, safe(question.getFieldId()), safe(question.getQuestion()), "Skipped as requested");
                }
            }
            int recognized = 0;
            for (Frame frame : formPage.frames()) {
                Locator controls = frame.locator("input:not([type='hidden']):not([type='file']), textarea, select, [role='combobox'], [role='checkbox']");
                int count = Math.min(controls.count(), 300);
                for (int index = 0; index < count; index++) {
                    Locator control = controls.nth(index);
                    try {
                        if (!control.isVisible() || !control.isEnabled()) continue;
                        String label = fieldLabel(control).trim();
                        if (label.isBlank()) {
                            if (!isRequired(control, label)) continue;
                            label = "Unlabeled required Keka field " + (index + 1);
                        }
                        if (isCaptchaLabel(label)) continue;
                        // The user asked to leave this optional Keka field untouched.
                        if (isLastWorkingDate(label)) continue;
                        if (isPrivacyConsent(label, control)) {
                            checkIfUnchecked(control);
                            resolveQuestion(user, applicationId, label, control, "Accepted", savedQuestions);
                            continue;
                        }

                        String answer = isExperienceMonthsControl(control, label)
                                ? "0"
                                : trustedAnswer(user, label, control);
                        if (answer == null) answer = savedAnswer(control, label, savedQuestions);
                        if (answer == null) answer = savedProfileAnswer(user, control, label);
                        boolean required = isRequired(control, label) || shouldAskUser(label);
                        if (answer == null || answer.isBlank()) {
                            if (required) {
                                fillTemporaryUserInput(control, label);
                                recordPending(user, applicationId, label, control, savedQuestions);
                            }
                            continue;
                        }

                        boolean filled = isLastWorkingDate(label)
                                ? fillCalendarDate(formPage, control, answer)
                                : fillControl(formPage, control, answer);
                        if (!filled && required) {
                            // A saved answer that is not present in a live dropdown must be confirmed again.
                            fillTemporaryUserInput(control, label);
                            recordPending(user, applicationId, label, control, savedQuestions);
                        } else if (filled) {
                            resolveQuestion(user, applicationId, label, control, answer, savedQuestions);
                            recognized++;
                        }
                    } catch (Exception fieldError) {
                        String label = safe(fieldLabel(control));
                        if (!label.isBlank() && (isRequired(control, label) || shouldAskUser(label))) {
                            fillTemporaryUserInput(control, label);
                            recordPending(user, applicationId, label, control, savedQuestions);
                        }
                    }
                }
            }

            checkPrivacyAgreement(formPage);
            boolean hasPendingRequired = savedQuestions.stream().anyMatch(q -> q.isRequired()
                    && "PENDING".equalsIgnoreCase(safe(q.getStatus())));

            if (recognized < 5) {
                return GeminiApplicationAgent.Result.failed("Keka fields were detected, but fewer than five known profile fields could be matched. The form was not submitted.");
            }
            if (hasPendingRequired) {
                return GeminiApplicationAgent.Result.userInputRequired(
                        "Keka needs answers saved in Pending Approvals before this application can be submitted.");
            }
            if (isCaptchaPresent(formPage)) return captchaRequired();

            Locator submit = findAction(formPage, "apply", "apply now", "submit application", "submit");
            if (submit == null) return GeminiApplicationAgent.Result.failed("Keka final Apply/Submit control was not found.");
            submit.scrollIntoViewIfNeeded();
            submit.click();
            formPage.waitForTimeout(5000L);
            formPage = latestPage(formPage);
            if (isCaptchaPresent(formPage)) return captchaRequired();
            if (hasSubmissionConfirmation(formPage)) {
                return GeminiApplicationAgent.Result.success("Keka application submitted successfully.", formPage.url());
            }
            return GeminiApplicationAgent.Result.failed("Keka Apply was clicked, but a submission confirmation was not detected. Please review the open Chromium page.");
        } catch (Exception e) {
            return GeminiApplicationAgent.Result.failed("Keka automation failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    private String trustedAnswer(User user, String label, Locator control) {
        String n = normalize(label);
        if (has(n, "first name", "given name")) return firstName(user);
        if (has(n, "last name", "surname", "family name")) return lastName(user);
        if (n.equals("email") || n.contains("email address")) return safe(user.getEmail());
        if (n.equals("gender")) return safe(user.getGender());
        if (isCountryCodeControl(label, control)) return safe(user.getCountryCode());
        if ((n.contains("mobile") || n.contains("phone") || n.contains("contact")) && !n.contains("country")) return phoneNumber(user);
        if (n.equals("experience") || n.contains("experience years") || n.contains("years of experience") || n.contains("total experience")) {
            return user.getUserExperience() == null ? "" : String.valueOf(user.getUserExperience());
        }
        if (n.contains("current location") || n.equals("location")) return safe(user.getLocation());
        if (has(n, "current salary", "current ctc", "present salary", "present ctc")) {
            if (user.getUserExperience() == null || user.getUserExperience() <= 1) return "0";
            return additionalDetail(user, "current salary", "current ctc", "present salary", "present ctc");
        }
        if (has(n, "expected salary", "expected ctc", "salary expectation")) {
            return additionalDetail(user, "expected salary", "expected ctc", "salary expectation");
        }
        if (has(n, "available days to join", "days to join", "availability", "notice period")) {
            return additionalDetail(user, "available days to join", "days to join", "availability", "notice period");
        }
        if (n.contains("preferred") && (n.contains("location") || n.contains("city"))) {
            return additionalDetail(user, "preferred location", "preferred city");
        }
        return null;
    }

    private boolean shouldAskUser(String label) {
        String n = normalize(label);
        return has(n, "current salary", "current ctc", "present salary", "present ctc",
                "expected salary", "expected ctc", "salary expectation",
                "available days to join", "available to join", "days to join", "join in days");
    }

    private boolean isCountryCodeControl(String label, Locator control) {
        String n = normalize(label);
        if (n.contains("country code") || n.contains("dial code") || n.contains("calling code")) return true;
        if (!isDropdown(control) && !"select".equals(safe(String.valueOf(control.evaluate("e=>e.tagName.toLowerCase()"))))) return false;
        if (!(n.contains("phone") || n.contains("mobile") || n.contains("contact"))) return false;
        try {
            if (safe(String.valueOf(control.evaluate("e=>e.tagName.toLowerCase()"))).equals("select")) {
                Locator options = control.locator("option");
                for (int i = 0; i < Math.min(options.count(), 100); i++) {
                    String option = safe(options.nth(i).innerText());
                    if (option.contains("+") || option.toLowerCase(Locale.ROOT).contains("india")) return true;
                }
            }
            return true;
        } catch (Exception ignored) { return true; }
    }

    private boolean fillControl(Page page, Locator control, String answer) {
        String tag = safe(String.valueOf(control.evaluate("e=>e.tagName.toLowerCase()")));
        if (safe(control.getAttribute("type")).equalsIgnoreCase("date")) {
            control.fill(answer);
            return true;
        }
        if (tag.equals("select")) {
            Locator options = control.locator("option");
            for (int i = 0; i < options.count(); i++) {
                Locator option = options.nth(i);
                String text = safe(option.innerText());
                String value = safe(option.getAttribute("value"));
                if (matchesOption(text, answer) || matchesOption(value, answer)) {
                    control.selectOption(value);
                    return true;
                }
            }
            return false;
        }
        if (isDropdown(control)) {
            control.click();
            if (tag.equals("input")) {
                control.fill(answer);
                page.waitForTimeout(1000L);
            }
            Locator options = page.locator("[role='option']:visible, [role='listbox'] *:visible, li:visible, [data-value]:visible");
            for (int i = 0; i < Math.min(options.count(), 100); i++) {
                Locator option = options.nth(i);
                String text = safe(option.innerText());
                if (!text.isBlank() && matchesOption(text, answer)) {
                    option.click();
                    return true;
                }
            }
            return isCurrentValue(control, answer);
        }
        String type = safe(control.getAttribute("type")).toLowerCase(Locale.ROOT);
        if (type.equals("checkbox") || type.equals("radio") || tag.equals("button")) return false;
        control.fill(answer);
        return true;
    }

    private boolean fillCalendarDate(Page page, Locator control, String answer) {
        String isoDate = normalizeDate(answer);
        if (isoDate.isBlank()) return false;
        String day = String.valueOf(Integer.parseInt(isoDate.substring(8, 10)));
        try {
            control.click();
            Locator dateCell = page.locator("[role='gridcell']:visible, button:visible, [role='button']:visible, td:visible")
                    .filter(new Locator.FilterOptions().setHasText(java.util.regex.Pattern.compile("^\\s*" + java.util.regex.Pattern.quote(day) + "\\s*$")));
            if (!safe(control.getAttribute("type")).equalsIgnoreCase("date") && dateCell.count() > 0) {
                dateCell.last().click();
                return true;
            }
        } catch (Exception ignored) { }
        try {
            control.fill(isoDate);
            return true;
        } catch (Exception ignored) { return false; }
    }

    private String normalizeDate(String answer) {
        if (answer == null || answer.isBlank()) return "";
        try { return java.time.LocalDate.parse(answer.trim()).toString(); } catch (Exception ignored) { }
        for (java.time.format.DateTimeFormatter format : List.of(
                java.time.format.DateTimeFormatter.ofPattern("d/M/uuuu"),
                java.time.format.DateTimeFormatter.ofPattern("d-M-uuuu"),
                java.time.format.DateTimeFormatter.ofPattern("M/d/uuuu"),
                java.time.format.DateTimeFormatter.ofPattern("d MMM uuuu", Locale.ENGLISH),
                java.time.format.DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH))) {
            try { return java.time.LocalDate.parse(answer.trim(), format).toString(); } catch (Exception ignored) { }
        }
        return "";
    }

    private boolean isLastWorkingDate(String label) {
        String n = normalize(label);
        return n.contains("last working day") || n.contains("relieving date");
    }

    private boolean isExperienceMonthsControl(Locator control, String label) {
        String n = normalize(label);
        if (n.contains("month")) return true;
        try {
            String id = safe(control.getAttribute("id")) + " " + safe(control.getAttribute("name"))
                    + " " + safe(control.getAttribute("aria-label"));
            if (normalize(id).contains("month")) return true;
            return Boolean.TRUE.equals(control.evaluate("""
                    e=>{let p=e;for(let i=0;p&&i<6;i++,p=p.parentElement){
                      const t=(p.innerText||'').toLowerCase();
                      if(t.includes('experience')&&t.includes('month')){
                        const fields=[...p.querySelectorAll('input:not([type=hidden]),select,[role=combobox]')];
                        if(fields.length>=2)return fields.indexOf(e)>0;
                      }
                    }return false}
                    """));
        } catch (Exception ignored) { return false; }
    }

    private void checkPrivacyAgreement(Page page) {
        try {
            Locator labels = page.locator("label:visible");
            for (int i = 0; i < Math.min(labels.count(), 100); i++) {
                Locator label = labels.nth(i);
                String text = normalize(safe(label.innerText()));
                if (text.contains("data processing") || text.contains("privacy policy")) {
                    try { label.click(); } catch (Exception ignored) { }
                    break;
                }
            }
            Locator checks = page.locator("input[type='checkbox']");
            for (int i = 0; i < checks.count(); i++) {
                Locator checkbox = checks.nth(i);
                String labelsText = safe(String.valueOf(checkbox.evaluate("e=>[...(e.labels||[])].map(l=>l.innerText).join(' ')") ) );
                if (normalize(labelsText).contains("data processing") || normalize(labelsText).contains("privacy policy")) {
                    try { if (!checkbox.isChecked()) checkbox.check(new Locator.CheckOptions().setForce(true)); } catch (Exception ignored) { }
                }
            }
        } catch (Exception ignored) { }
    }

    private void fillTemporaryUserInput(Locator control, String label) {
        try {
            String tag = safe(String.valueOf(control.evaluate("e=>e.tagName.toLowerCase()")));
            String type = safe(control.getAttribute("type")).toLowerCase(Locale.ROOT);
            String n = normalize(label);
            if ((tag.equals("textarea") || tag.equals("input") && (type.isBlank() || type.equals("text")))
                    && !has(n, "email", "phone", "mobile", "contact", "date", "number", "captcha", "password")) {
                control.fill("PENDING_USER_INPUT");
            }
        } catch (Exception ignored) { }
    }

    private boolean isDropdown(Locator control) {
        try {
            return "combobox".equalsIgnoreCase(safe(control.getAttribute("role")))
                    || safe(control.getAttribute("aria-haspopup")).length() > 0
                    || "true".equalsIgnoreCase(safe(control.getAttribute("aria-expanded")));
        } catch (Exception ignored) { return false; }
    }

    private Locator findFileInput(Page page) {
        for (Frame frame : page.frames()) {
            Locator files = frame.locator("input[type='file']");
            if (files.count() > 0) return files.first();
        }
        return null;
    }

    private boolean hasResumeInput(Page page) { return findFileInput(page) != null; }

    private Locator findAction(Page page, String... names) {
        for (Frame frame : page.frames()) {
            Locator actions = frame.locator("button:visible, a:visible, [role='button']:visible, input[type='submit']:visible, input[type='button']:visible");
            for (int i = 0; i < actions.count(); i++) {
                Locator action = actions.nth(i);
                String text = normalize(safe(action.innerText()) + " " + safe(action.getAttribute("value")) + " " + safe(action.getAttribute("aria-label")));
                for (String name : names) if (text.equals(normalize(name))) return action;
            }
        }
        return null;
    }

    private String fieldLabel(Locator control) {
        return safe(String.valueOf(control.evaluate("""
                e=>{const n=s=>String(s||'').replace(/\\s+/g,' ').trim();
                const vals=[e.getAttribute('aria-label'),e.getAttribute('data-label'),e.getAttribute('placeholder'),e.getAttribute('name'),e.getAttribute('id')];
                if(e.labels&&e.labels.length){const x=[...e.labels].map(l=>n(l.innerText)).filter(Boolean).join(' ');if(x)return x}
                const by=(e.getAttribute('aria-labelledby')||'').split(/\\s+/).map(id=>document.getElementById(id)).filter(Boolean).map(x=>n(x.innerText)).filter(Boolean).join(' ');if(by)return by;
                let p=e;for(let i=0;p&&i<6;i++,p=p.parentElement){const x=p.querySelector(':scope > label,:scope > [class*=label],:scope > [class*=Label]');if(x&&n(x.innerText))return n(x.innerText)}
                return vals.map(n).find(Boolean)||''}
                """)));
    }

    private void recordPending(User user, Long applicationId, String label, Locator control, List<ApplicationQuestion> existingQuestions) {
        if (applicationId == null || label.isBlank()) return;
        String fieldId = firstNonBlank(safe(control.getAttribute("id")), safe(control.getAttribute("name")), "keka_" + normalize(label).replace(' ', '_'));
        ApplicationQuestion question = existingQuestions.stream()
                .filter(q -> fieldId.equalsIgnoreCase(safe(q.getFieldId())) || label.equalsIgnoreCase(safe(q.getQuestion())))
                .findFirst().orElse(null);
        if (question == null) {
            question = ApplicationQuestion.builder().applicationId(applicationId).question(label).fieldId(fieldId)
                    .questionType(fieldType(control)).availableOptions(optionsJson(control))
                    .temporaryAnswer(shouldUseTemporaryText(control, label) ? "PENDING_USER_INPUT" : "")
                    .status("PENDING").required(true).createdAt(LocalDateTime.now()).build();
            question = questionRepository.save(question);
            existingQuestions.add(question);
        } else if ("ANSWERED".equalsIgnoreCase(safe(question.getStatus())) || "RESOLVED".equalsIgnoreCase(safe(question.getStatus()))) {
            question.setStatus("PENDING");
            question.setFinalAnswer("");
            question.setTemporaryAnswer(shouldUseTemporaryText(control, label) ? "PENDING_USER_INPUT" : "");
            question.setAnsweredAt(null);
            question.setAvailableOptions(optionsJson(control));
            questionRepository.save(question);
        } else if ("PENDING".equalsIgnoreCase(safe(question.getStatus()))
                && safe(question.getTemporaryAnswer()).isBlank() && shouldUseTemporaryText(control, label)) {
            question.setTemporaryAnswer("PENDING_USER_INPUT");
            questionRepository.save(question);
        }
        if ("PENDING".equalsIgnoreCase(safe(question.getStatus()))) persistPending(user, question);
    }

    private void resolveQuestion(User user, Long applicationId, String label, Locator control, String answer, List<ApplicationQuestion> questions) {
        if (applicationId == null) return;
        String id = firstNonBlank(safe(control.getAttribute("id")), safe(control.getAttribute("name")));
        for (ApplicationQuestion question : questions) {
            if ((label.equalsIgnoreCase(safe(question.getQuestion())) || (!id.isBlank() && id.equalsIgnoreCase(safe(question.getFieldId())))
                    ) && "PENDING".equalsIgnoreCase(safe(question.getStatus()))) {
                question.setStatus("RESOLVED");
                question.setFinalAnswer(answer);
                question.setAnsweredAt(LocalDateTime.now());
                questionRepository.save(question);
            }
        }
        updatePendingProfile(user, id, label, answer);
    }

    private void persistPending(User user, ApplicationQuestion question) {
        try {
            ObjectNode root = readOtherDetails(user);
            ObjectNode profile = root.path("application_profile").isObject()
                    ? (ObjectNode) root.path("application_profile") : objectMapper.createObjectNode();
            root.set("application_profile", profile);
            ObjectNode pending = profile.path("pending_questions").isObject()
                    ? (ObjectNode) profile.path("pending_questions") : objectMapper.createObjectNode();
            profile.set("pending_questions", pending);
            ObjectNode item = objectMapper.createObjectNode();
            item.put("question", question.getQuestion());
            item.put("fieldId", question.getFieldId());
            item.put("applicationId", question.getApplicationId());
            item.put("type", question.getQuestionType());
            item.put("required", question.isRequired());
            item.put("temporaryAnswer", safe(question.getTemporaryAnswer()));
            item.put("answer", "");
            item.put("status", question.getStatus());
            try { JsonNode options = objectMapper.readTree(safe(question.getAvailableOptions())); if (options.isArray()) item.set("options", options); }
            catch (Exception ignored) { }
            pending.set(question.getFieldId(), item);
            user.setOtherDetails(objectMapper.writeValueAsString(root));
            userRepository.save(user);
        } catch (Exception ignored) { }
    }

    private void updatePendingProfile(User user, String id, String label, String answer) {
        try {
            ObjectNode root = readOtherDetails(user);
            JsonNode pendingNode = root.path("application_profile").path("pending_questions");
            if (pendingNode instanceof ObjectNode pending) {
                var entries = pending.properties().iterator();
                while (entries.hasNext()) {
                    var entry = entries.next();
                    JsonNode item = entry.getValue();
                    if (item instanceof ObjectNode obj && (id.equalsIgnoreCase(obj.path("fieldId").asText())
                            || label.equalsIgnoreCase(obj.path("question").asText()))) {
                        obj.put("answer", answer);
                        obj.put("status", "RESOLVED");
                    }
                }
            }
            user.setOtherDetails(objectMapper.writeValueAsString(root));
            userRepository.save(user);
        } catch (Exception ignored) { }
    }

    private ObjectNode readOtherDetails(User user) {
        try {
            JsonNode root = objectMapper.readTree(safe(user.getOtherDetails()));
            return root instanceof ObjectNode object ? object : objectMapper.createObjectNode();
        } catch (Exception ignored) { return objectMapper.createObjectNode(); }
    }

    private String savedAnswer(Locator control, String label, List<ApplicationQuestion> questions) {
        String id = firstNonBlank(safe(control.getAttribute("id")), safe(control.getAttribute("name")));
        return questions.stream().filter(q -> "ANSWERED".equalsIgnoreCase(safe(q.getStatus())) || "RESOLVED".equalsIgnoreCase(safe(q.getStatus())))
                .filter(q -> label.equalsIgnoreCase(safe(q.getQuestion())) || (!id.isBlank() && id.equalsIgnoreCase(safe(q.getFieldId()))))
                .map(ApplicationQuestion::getFinalAnswer).map(this::safe).filter(v -> !v.isBlank()).findFirst().orElse(null);
    }

    private String savedProfileAnswer(User user, Locator control, String label) {
        try {
            JsonNode root = objectMapper.readTree(safe(user.getOtherDetails())).path("application_profile");
            String wanted = normalize(label);
            JsonNode pending = root.path("pending_questions");
            if (pending.isObject()) {
                var entries = pending.properties().iterator();
                while (entries.hasNext()) {
                    JsonNode item = entries.next().getValue();
                    if (wanted.equals(normalize(item.path("question").asText()))) {
                        String answer = safe(item.path("answer").asText());
                        if (!answer.isBlank()) return answer;
                    }
                }
            }
            JsonNode answers = root.path("answers");
            if (answers.isObject()) {
                var entries = answers.properties().iterator();
                while (entries.hasNext()) {
                    var entry = entries.next();
                    if (wanted.equals(normalize(entry.getKey())) && entry.getValue().isValueNode()) {
                        String answer = safe(entry.getValue().asText());
                        if (!answer.isBlank()) return answer;
                    }
                }
            }
        } catch (Exception ignored) { }
        return null;
    }

    private String additionalDetail(User user, String... keys) {
        try { return findAdditionalDetail(objectMapper.readTree(safe(user.getOtherDetails())), List.of(keys)); }
        catch (Exception ignored) { return ""; }
    }

    private String findAdditionalDetail(JsonNode node, List<String> keys) {
        if (node == null) return "";
        if (node.isObject()) {
            String questionText = normalize(node.path("question").asText());
            String answerText = safe(node.path("answer").asText());
            if (!answerText.isBlank() && keys.stream().anyMatch(k -> questionText.equals(normalize(k))
                    || questionText.startsWith(normalize(k) + " "))) return answerText;
            var fields = node.properties().iterator();
            while (fields.hasNext()) {
                var field = fields.next();
                String fieldKey = normalize(field.getKey());
                if (keys.stream().anyMatch(k -> fieldKey.equals(normalize(k))
                        || fieldKey.startsWith(normalize(k) + " "))) {
                    if (field.getValue().isValueNode() && !field.getValue().asText().isBlank()) return field.getValue().asText().trim();
                }
                String nested = findAdditionalDetail(field.getValue(), keys);
                if (!nested.isBlank()) return nested;
            }
        } else if (node.isArray()) {
            for (JsonNode value : node) { String nested = findAdditionalDetail(value, keys); if (!nested.isBlank()) return nested; }
        }
        return "";
    }

    private String optionsJson(Locator control) {
        try {
            List<String> options = new ArrayList<>();
            if ("select".equals(safe(String.valueOf(control.evaluate("e=>e.tagName.toLowerCase()"))))) {
                Locator values = control.locator("option");
                for (int i = 0; i < Math.min(values.count(), 100); i++) {
                    String option = safe(values.nth(i).innerText());
                    if (!option.isBlank()) options.add(option);
                }
            } else {
                if (isDropdown(control)) {
                    try { control.click(); } catch (Exception ignored) { }
                }
                Object values = control.evaluate("e=>{const d=e.ownerDocument;const ids=(e.getAttribute('aria-controls')||'').split(/\\s+/).filter(Boolean);const roots=ids.map(id=>d.getElementById(id)).filter(Boolean);const ns=roots.flatMap(r=>[...(r.matches('[role=option],li,[data-value]')?[r]:[]),...r.querySelectorAll('[role=option],li,[data-value]')]);const all=ns.length?ns:[...d.querySelectorAll('[role=option]')];return all.filter(x=>{const r=x.getBoundingClientRect(),s=getComputedStyle(x);return r.width>0&&r.height>0&&s.display!=='none'&&s.visibility!=='hidden'}).map(x=>(x.innerText||'').trim()).filter(Boolean).slice(0,100)}");
                if (values instanceof List<?> list) for (Object value : list) if (value != null && !value.toString().isBlank()) options.add(value.toString().trim());
                try { control.press("Escape"); } catch (Exception ignored) { }
            }
            return options.isEmpty() ? "" : objectMapper.writeValueAsString(options);
        } catch (Exception ignored) { return ""; }
    }

    private String fieldType(Locator control) {
        String tag = safe(String.valueOf(control.evaluate("e=>e.tagName.toLowerCase()")));
        String type = safe(control.getAttribute("type"));
        if (tag.equals("select") || isDropdown(control)) return "select";
        if (type.equalsIgnoreCase("checkbox")) return "checkbox";
        if (type.equalsIgnoreCase("radio")) return "radio";
        if (type.equalsIgnoreCase("date") || isLastWorkingDate(fieldLabel(control))) return "date";
        if (tag.equals("textarea")) return "textarea";
        return "text";
    }

    private boolean shouldUseTemporaryText(Locator control, String label) {
        String tag = safe(String.valueOf(control.evaluate("e=>e.tagName.toLowerCase()")));
        String type = safe(control.getAttribute("type")).toLowerCase(Locale.ROOT);
        String n = normalize(label);
        return (tag.equals("textarea") || tag.equals("input") && (type.isBlank() || type.equals("text")))
                && !has(n, "email", "phone", "mobile", "contact", "date", "number", "captcha", "password");
    }

    private boolean isRequired(Locator control, String label) {
        return "true".equalsIgnoreCase(safe(control.getAttribute("required")))
                || "true".equalsIgnoreCase(safe(control.getAttribute("aria-required"))) || label.contains("*");
    }

    private boolean isPrivacyConsent(String label, Locator control) {
        String type = safe(control.getAttribute("type"));
        String n = normalize(label);
        return (type.equalsIgnoreCase("checkbox") || "checkbox".equalsIgnoreCase(safe(control.getAttribute("role"))))
                && (n.contains("privacy") || n.contains("data protection"));
    }

    private void checkIfUnchecked(Locator control) {
        try { if (!control.isChecked()) control.check(); } catch (Exception ignored) { }
    }

    private void acceptCookieConsent(Page page) {
        Locator actions = page.locator("button:visible, [role='button']:visible, a:visible");
        for (int i = 0; i < Math.min(actions.count(), 120); i++) {
            Locator action = actions.nth(i);
            String text = normalize(safe(action.innerText()) + " " + safe(action.getAttribute("aria-label")));
            if (text.equals("accept all") || text.equals("accept all cookies") || text.equals("accept cookies") || text.equals("allow all cookies") || text.equals("agree")) {
                action.click();
                page.waitForTimeout(500L);
                return;
            }
        }
    }

    private boolean isCaptchaPresent(Page page) {
        try {
            String body = normalize(page.locator("body").innerText());
            return body.contains("captcha") || body.contains("verify you are human")
                    || body.contains("security challenge") || page.locator("iframe[src*='captcha' i], iframe[src*='recaptcha' i], .g-recaptcha, [data-sitekey]").count() > 0;
        } catch (Exception ignored) { return false; }
    }

    private boolean isCaptchaLabel(String label) { return normalize(label).contains("captcha"); }

    private GeminiApplicationAgent.Result captchaRequired() {
        return GeminiApplicationAgent.Result.blocked(
                "Keka form details are filled. Please solve the CAPTCHA and click Apply in the open Chromium window within 3 minutes.");
    }

    private boolean hasSubmissionConfirmation(Page page) {
        try {
            String body = normalize(page.locator("body").innerText());
            return body.contains("application submitted") || body.contains("successfully submitted")
                    || body.contains("thank you for applying") || body.contains("application received")
                    || body.contains("successfully applied");
        } catch (Exception ignored) { return false; }
    }

    private Page latestPage(Page page) {
        try { List<Page> pages = page.context().pages(); return pages.isEmpty() ? page : pages.get(pages.size() - 1); }
        catch (Exception ignored) { return page; }
    }

    private String firstName(User user) {
        if (!safe(user.getFirstName()).isBlank()) return safe(user.getFirstName());
        String[] parts = safe(user.getName()).trim().split("\\s+");
        return parts.length <= 1 ? (parts.length == 0 ? "" : parts[0]) : String.join(" ", java.util.Arrays.copyOf(parts, parts.length - 1));
    }

    private String lastName(User user) {
        if (!safe(user.getLastName()).isBlank()) return safe(user.getLastName());
        String[] parts = safe(user.getName()).trim().split("\\s+");
        return parts.length <= 1 ? "" : parts[parts.length - 1];
    }

    private String phoneNumber(User user) {
        String phone = safe(user.getPhone());
        String code = safe(user.getCountryCode()).replaceAll("[^0-9]", "");
        String digits = phone.replaceAll("[^0-9]", "");
        if (!code.isBlank() && digits.startsWith(code) && digits.length() > code.length()) return digits.substring(code.length());
        return digits;
    }

    private boolean matchesOption(String option, String answer) {
        String o = normalize(option), a = normalize(answer);
        return o.equals(a) || (!a.isBlank() && o.contains(a)) || (!o.isBlank() && a.contains(o));
    }

    private boolean isCurrentValue(Locator control, String answer) {
        try {
            String tag = safe(String.valueOf(control.evaluate("e=>e.tagName.toLowerCase()")));
            String value = tag.equals("select") ? safe(control.locator("option:checked").innerText())
                    : safe(String.valueOf(control.evaluate("e=>e.value||e.getAttribute('aria-valuetext')||e.innerText||''")));
            return matchesOption(value, answer);
        } catch (Exception ignored) { return false; }
    }

    private boolean has(String value, String... phrases) {
        for (String phrase : phrases) if (value.contains(normalize(phrase))) return true;
        return false;
    }

    private String firstNonBlank(String... values) { for (String value : values) if (value != null && !value.isBlank()) return value; return ""; }
    private String normalize(String value) { return safe(value).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}+]+", " ").trim(); }
    private String safe(String value) { return value == null ? "" : value.trim(); }
}
