package com.jobscraper.job_platform.profile;

import com.jobscraper.job_platform.resume.GeminiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class ProjectAtsQuestionController {
    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    @PostMapping("/ats-questions")
    public ResponseEntity<?> questions(@RequestBody ProjectTitleRequest request, Authentication authentication) {
        if (authentication == null || request == null || request.projectTitle() == null || request.projectTitle().isBlank()) {
            return ResponseEntity.badRequest().body("Enter a project title first.");
        }
        String prompt = """
                Create 5 to 7 concise questions for a candidate to describe this project clearly in an ATS-friendly resume.
                Project title: %s
                Ask only for verifiable details: problem/user, candidate's role, implementation and architecture,
                technologies (if not already known), important engineering decisions, testing/deployment,
                measurable outcomes (allow 'not measured'), and individual contribution.
                Do not invent answers, metrics, employers, or project claims. Avoid generic duplicate questions.
                Return JSON only as an array of objects with keys: key (short snake_case), question, type ('text' or 'textarea'), placeholder.
                """.formatted(request.projectTitle().trim());
        try {
            String raw = geminiClient.generate(prompt, 1200).trim();
            int start = raw.indexOf('['), end = raw.lastIndexOf(']');
            if (start < 0 || end <= start) throw new IllegalArgumentException("Gemini returned no question array.");
            JsonNode parsed = objectMapper.readTree(raw.substring(start, end + 1));
            if (!parsed.isArray()) throw new IllegalArgumentException("Gemini question response is not an array.");
            List<ProjectQuestion> questions = new ArrayList<>();
            for (JsonNode item : parsed) {
                String key = item.path("key").asText("").trim();
                String question = item.path("question").asText("").trim();
                if (key.isBlank() || question.isBlank()) continue;
                String type = item.path("type").asText("textarea").equalsIgnoreCase("text") ? "text" : "textarea";
                questions.add(new ProjectQuestion(key, question, type, item.path("placeholder").asText("")));
            }
            if (questions.isEmpty()) return ResponseEntity.internalServerError().body("Could not create project questions. Try again.");
            return ResponseEntity.ok(questions);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Could not create project questions. Try again.");
        }
    }

    public record ProjectTitleRequest(String projectTitle) { }
    public record ProjectQuestion(String key, String question, String type, String placeholder) { }
}
