package com.jobscraper.job_platform.coldmail;

import com.jobscraper.job_platform.resume.GeminiClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ColdMailService {

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;

    public ColdMailResult generateColdMail(
            String userData,
            String jobData,
            String hrEmail
    ) {

        String prompt = """
                You are a professional recruiter outreach email writer.

                Generate a concise, personalized cold email for a job candidate
                contacting a recruiter/HR regarding the target job.

                CANDIDATE INFORMATION:
                %s

                TARGET JOB:
                %s

                HR EMAIL:
                %s

                RULES:

                1. Do not fabricate any candidate information.
                2. Use only skills, education, experience, projects,
                   certifications and achievements provided.
                3. Mention the target job title and company naturally.
                4. Keep the email professional and concise.
                5. Do not claim that the candidate has already been selected.
                6. Do not make unrealistic claims.
                7. Do not include a resume attachment statement unless appropriate.
                8. The purpose is to introduce the candidate and express interest.
                9. Return ONLY valid JSON.
                10. Do not use Markdown code fences.

                Required JSON format:

                {
                  "subject": "email subject",
                  "body": "complete email body"
                }
                """.formatted(
                userData,
                jobData,
                hrEmail == null ? "" : hrEmail
        );

        String response = geminiClient.generate(prompt);

        String json = cleanJson(response);

        try {

            JsonNode root =
                    objectMapper.readTree(json);

            JsonNode subjectNode =
                    root.get("subject");

            JsonNode bodyNode =
                    root.get("body");

            if (subjectNode == null || bodyNode == null) {
                throw new RuntimeException(
                        "Gemini cold mail response is missing subject or body"
                );
            }

            String subject =
                    subjectNode.asText().trim();

            String body =
                    bodyNode.asText().trim();

            if (subject.isBlank() || body.isBlank()) {
                throw new RuntimeException(
                        "Gemini generated an empty cold email"
                );
            }

            return new ColdMailResult(
                    subject,
                    body
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Failed to parse Gemini cold mail response: "
                            + e.getMessage(),
                    e
            );
        }
    }

    private String cleanJson(String response) {

        String cleaned = response.trim();

        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }

        if (cleaned.endsWith("```")) {
            cleaned =
                    cleaned.substring(
                            0,
                            cleaned.length() - 3
                    );
        }

        return cleaned.trim();
    }

    public record ColdMailResult(
            String subject,
            String body
    ) {
    }
}