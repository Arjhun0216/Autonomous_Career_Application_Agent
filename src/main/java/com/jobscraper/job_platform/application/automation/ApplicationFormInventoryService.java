package com.jobscraper.job_platform.application.automation;

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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * First-pass inventory of the live application form. This service discovers
 * fields and option lists without selecting an answer or submitting the form.
 */
@Service
@RequiredArgsConstructor
public class ApplicationFormInventoryService {
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    public String discoverAndPersist(Page page, User user, Long applicationId) {
        ObjectNode inventory = objectMapper.createObjectNode();
        inventory.put("applicationId", applicationId == null ? -1 : applicationId);
        inventory.put("url", safe(page.url()));
        inventory.put("title", safe(page.title()));
        inventory.put("capturedAt", LocalDateTime.now().toString());
        inventory.put("structureFingerprint", structureFingerprint(page));
        ArrayNode fields = inventory.putArray("fields");
        Set<String> seen = new LinkedHashSet<>();

        int frameIndex = 0;
        for (Frame frame : page.frames()) {
            final int currentFrame = frameIndex++;
            try {
                Locator controls = frame.locator("input, textarea, select, lyte-dropdown, [role='combobox'], [role='radio'], [role='checkbox'], [contenteditable='true']");
                int count = Math.min(controls.count(), 300);
                for (int i = 0; i < count; i++) {
                    Locator control = controls.nth(i);
                    try {
                        if (!control.isVisible()) continue;
                        ObjectNode field = inspectControl(control, currentFrame, i);
                        String key = field.path("fieldId").asText("") + "|" + field.path("label").asText("");
                        if (!seen.add(key)) continue;
                        fields.add(field);
                    } catch (Exception ignored) { }
                }

                // Some custom widgets only render their options after opening.
                Locator combos = frame.locator("[role='combobox'], .lyteDummyEventContainer");
                int comboCount = Math.min(combos.count(), 100);
                for (int i = 0; i < comboCount; i++) {
                    Locator combo = combos.nth(i);
                    try {
                        if (!combo.isVisible() || !combo.isEnabled()) continue;
                        String controlId = safe(String.valueOf(combo.evaluate("e => e.closest('lyte-dropdown')?.id || e.id || ''")));
                        String controlsId = safe(combo.getAttribute("aria-controls"));
                        String label = safe(String.valueOf(combo.evaluate("e => { const n=s=>(s||'').replace(/\\s+/g,' ').trim(); const a=e.getAttribute('aria-labelledby'); if(a){const t=a.split(/\\s+/).map(x=>document.getElementById(x)).filter(Boolean).map(x=>n(x.innerText)).filter(Boolean).join(' ');if(t)return t;} let p=e.closest('lyte-dropdown,.form-group,[class*=field],[class*=Field],li'); for(let j=0;p&&j<4;j++,p=p.parentElement){let x=p.querySelector('label,[class*=label],[class*=question]');if(x&&n(x.innerText))return n(x.innerText);} return n(e.innerText); }")));
                        combo.click();
                        page.waitForTimeout(80);
                        List<String> options = visibleOptions(page);
                        if (!options.isEmpty()) {
                            ObjectNode target = findField(fields, controlId, controlsId, label);
                            if (target != null) {
                                ArrayNode optionArray = target.putArray("options");
                                options.forEach(optionArray::add);
                                target.put("optionsStatus", "CAPTURED_AFTER_OPEN");
                            }
                        }
                        try { combo.press("Escape"); } catch (Exception ignored) { }
                    } catch (Exception ignored) {
                        try { combo.press("Escape"); } catch (Exception ignored2) { }
                    }
                }
            } catch (Exception ignored) { }
        }

        inventory.put("fieldCount", fields.size());
        String serialized = inventory.toString();
        persist(user, serialized);
        System.out.println("APPLICATION FORM INVENTORY: application=" + applicationId + ", fields=" + fields.size());
        return serialized;
    }

    /** Fingerprints form structure, not values, so new sections on the same URL are rediscovered. */
    public String structureFingerprint(Page page) {
        StringBuilder structure = new StringBuilder();
        int frameIndex = 0;
        for (Frame frame : page.frames()) {
            try {
                Locator controls = frame.locator("input:visible, textarea:visible, select:visible, lyte-dropdown:visible, [role='combobox']:visible, [role='radio']:visible, [role='checkbox']:visible, [contenteditable='true']:visible");
                int count = Math.min(controls.count(), 300);
                structure.append("frame:").append(frameIndex).append('|').append(safe(frame.url())).append('|');
                for (int i = 0; i < count; i++) {
                    Locator control = controls.nth(i);
                    String signature = safe(String.valueOf(control.evaluate("e => { const n=s=>(s||'').replace(/\\s+/g,' ').trim(); const tag=e.tagName.toLowerCase(); const label=(e.getAttribute('aria-label')||e.getAttribute('placeholder')||e.getAttribute('name')||e.id||''); const parent=e.closest('lyte-dropdown,.form-group,[class*=field],[class*=Field],li'); const p=parent&&parent.querySelector('label,[class*=label],[class*=question]'); return [tag,e.getAttribute('type')||'',e.getAttribute('role')||'',e.id||'',e.getAttribute('name')||'',n(label),n(p&&p.innerText)].join('~'); }")));
                    structure.append(signature).append(';');
                }
            } catch (Exception ignored) { }
            frameIndex++;
        }
        return Integer.toHexString(structure.toString().hashCode()) + ":" + structure.length();
    }

    public boolean isApplicationFormReady(Page page) {
        try {
            String text = safe(page.locator("body").innerText()).toLowerCase(Locale.ROOT);
            boolean parsed = text.contains("resume parsed successfully") || text.contains("resume parsing completed") || text.contains("review the extracted details");
            boolean hasUpload = page.locator("input[type='file']").count() > 0;
            int fields = 0;
            for (Frame frame : page.frames()) {
                try { fields += Math.min(frame.locator("input:visible, textarea:visible, select:visible, [role='combobox']:visible").count(), 100); }
                catch (Exception ignored) { }
            }
            return fields >= 2 && (parsed || !hasUpload);
        } catch (Exception e) { return false; }
    }

    private ObjectNode inspectControl(Locator control, int frameIndex, int index) {
        ObjectNode f = objectMapper.createObjectNode();
        String tag = safe(String.valueOf(control.evaluate("e => e.tagName.toLowerCase()")));
        String type = safe(control.getAttribute("type"));
        String id = safe(control.getAttribute("id"));
        String name = safe(control.getAttribute("name"));
        String label = safe(String.valueOf(control.evaluate("e => { const n=s=>(s||'').replace(/\\s+/g,' ').trim(); const id=e.id; let l=id?[...document.querySelectorAll('label[for]')].find(x=>x.htmlFor===id):null; if(l&&n(l.innerText))return n(l.innerText); if(e.labels&&e.labels.length){const t=[...e.labels].map(x=>n(x.innerText)).filter(Boolean).join(' ');if(t)return t;} const a=e.getAttribute('aria-label');if(a)return n(a); const b=e.getAttribute('aria-labelledby');if(b){const t=b.split(/\\s+/).map(x=>document.getElementById(x)).filter(Boolean).map(x=>n(x.innerText)).filter(Boolean).join(' ');if(t)return t;} let p=e.closest('lyte-dropdown,.form-group,[class*=field],[class*=Field],li'); for(let j=0;p&&j<4;j++,p=p.parentElement){const x=p.querySelector('label,[class*=label],[class*=question],[class*=field-name]');if(x&&n(x.innerText))return n(x.innerText);} return n(e.getAttribute('placeholder')) || n(e.innerText); }")));
        String current = "";
        try { current = "select".equals(tag) ? safe(control.locator("option:checked").innerText()) : safe(String.valueOf(control.evaluate("e => e.value ?? e.getAttribute('aria-valuetext') ?? e.innerText ?? ''"))); } catch (Exception ignored) { }
        f.put("frameIndex", frameIndex);
        f.put("index", index);
        f.put("fieldId", !id.isBlank() ? id : name);
        f.put("id", id);
        f.put("name", name);
        f.put("ariaControls", safe(control.getAttribute("aria-controls")));
        f.put("label", label);
        f.put("tag", tag);
        f.put("type", type.isBlank() ? tag : type);
        f.put("currentValue", current);
        f.put("required", "true".equalsIgnoreCase(safe(control.getAttribute("required"))) || "true".equalsIgnoreCase(safe(control.getAttribute("aria-required"))) || label.contains("*"));
        f.put("optionsStatus", "NOT_APPLICABLE");
        ArrayNode options = f.putArray("options");
        try {
            if ("select".equals(tag)) {
                Locator nodes = control.locator("option");
                for (int j = 0; j < Math.min(nodes.count(), 300); j++) {
                    String option = safe(nodes.nth(j).innerText()).trim();
                    if (!option.isBlank()) options.add(option);
                }
                f.put("optionsStatus", "CAPTURED");
            } else if (tag.contains("dropdown") || "combobox".equals(safe(control.getAttribute("role")))) {
                Locator nodes = control.locator("[role='option'], lyte-drop-item, li");
                for (int j = 0; j < Math.min(nodes.count(), 300); j++) {
                    String option = safe(nodes.nth(j).innerText()).trim();
                    if (!option.isBlank() && !options.toString().contains(option)) options.add(option);
                }
                f.put("optionsStatus", options.isEmpty() ? "NEEDS_OPEN" : "CAPTURED_IN_DOM");
            }
        } catch (Exception ignored) { }
        return f;
    }

    private List<String> visibleOptions(Page page) {
        Set<String> values = new LinkedHashSet<>();
        for (Frame frame : page.frames()) {
            try {
                Locator options = frame.locator("[role='option'], lyte-drop-item, [id^='Lyte_Drop_Item'], [class*='dropdown-option'], [class*='DropdownOption'], [class*='drop-item']");
                for (int i = 0; i < Math.min(options.count(), 300); i++) {
                    Locator option = options.nth(i);
                    if (!option.isVisible()) continue;
                    String value = safe(option.innerText()).replaceAll("\\s+", " ").trim();
                    if (!value.isBlank()) values.add(value);
                }
            } catch (Exception ignored) { }
        }
        return new ArrayList<>(values);
    }

    private ObjectNode findField(ArrayNode fields, String id, String controlsId, String label) {
        for (JsonNode n : fields) {
            if (!(n instanceof ObjectNode obj)) continue;
            String fieldId = obj.path("id").asText("");
            String fieldLabel = obj.path("label").asText("");
            if ((!id.isBlank() && id.equals(fieldId)) || (!controlsId.isBlank() && controlsId.equals(obj.path("ariaControls").asText(""))) || (!label.isBlank() && label.equalsIgnoreCase(fieldLabel))) return obj;
        }
        // Fallback: nearest custom control label is usually unique within a form.
        for (JsonNode n : fields) if (n instanceof ObjectNode obj && !label.isBlank() && label.equalsIgnoreCase(obj.path("label").asText(""))) return obj;
        return null;
    }

    private void persist(User user, String inventoryJson) {
        if (user == null) return;
        try {
            ObjectNode other;
            JsonNode parsed = safe(user.getOtherDetails()).isBlank() ? objectMapper.createObjectNode() : objectMapper.readTree(user.getOtherDetails());
            other = parsed instanceof ObjectNode o ? o : objectMapper.createObjectNode();
            JsonNode profileNode = other.get("application_profile");
            ObjectNode profile = profileNode instanceof ObjectNode o ? o : objectMapper.createObjectNode();

            ObjectNode current = (ObjectNode) objectMapper.readTree(inventoryJson);
            JsonNode oldInventoryNode = profile.get("form_inventory");
            ArrayNode history = objectMapper.createArrayNode();
            if (oldInventoryNode != null && oldInventoryNode.path("pages").isArray()) {
                oldInventoryNode.path("pages").forEach(history::add);
            }

            String currentUrl = current.path("url").asText("");
            String currentFingerprint = current.path("structureFingerprint").asText("");
            int existingIndex = -1;
            for (int i = 0; i < history.size(); i++) {
                JsonNode oldPage = history.get(i);
                if (currentUrl.equals(oldPage.path("url").asText("") )
                        && currentFingerprint.equals(oldPage.path("structureFingerprint").asText(""))) {
                    existingIndex = i;
                    break;
                }
            }
            if (existingIndex >= 0) history.set(existingIndex, current.deepCopy());
            else history.add(current.deepCopy());
            // Bound retained snapshots so a long application does not grow the user JSON indefinitely.
            while (history.size() > 20) history.remove(0);

            current.set("pages", history);
            current.put("discoveredPageCount", history.size());
            profile.set("form_inventory", current);
            other.set("application_profile", profile);
            user.setOtherDetails(other.toString());
            userRepository.save(user);
        } catch (Exception e) {
            System.out.println("Unable to persist application form inventory: " + e.getMessage());
        }
    }

    private String safe(String s) { return s == null ? "" : s; }
}
