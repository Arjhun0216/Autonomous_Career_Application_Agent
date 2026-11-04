package com.jobscraper.job_platform.skill;

import com.jobscraper.job_platform.resume.GeminiClient;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class SkillLearningService {

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;
    private final LowMatchSkillService skillService;

    public LearningResponse generateResources(
            String email,
            String skill,
            String experience
    ) {

        if (skill == null || skill.isBlank()) {
            throw new IllegalArgumentException(
                    "Skill is required"
            );
        }

        String safeExperience =
                experience == null
                        ? "Not specified"
                        : experience.trim();

        LowMatchSkillService.UserStack stack =
                skillService.getUserStack(email);

        String prompt =
                """
                You are a technical learning-path advisor.

                A candidate is missing or is uncertain about this skill:

                SKILL:
                """
                + skill
                + """

                CANDIDATE'S SELF-REPORTED EXPERIENCE WITH THE SKILL:
                """
                + safeExperience
                + """

                CANDIDATE STACK:
                Professional title:
                """
                + stack.professionalTitle()
                + """

                Existing skills:
                """
                + String.join(
                        ", ",
                        stack.skills()
                )
                + """

                Existing experience:
                """
                + stack.experience()
                + """

                Existing projects:
                """
                + stack.projects()
                + """

                Create a practical three-step learning path.

                RESOURCE 1 must answer:
                "What is this technology/concept?"

                RESOURCE 2 must answer:
                "How does it work internally and conceptually?"

                RESOURCE 3 must answer:
                "How do I use it with MY EXISTING STACK?"

                The third resource is critical.

                It must explicitly connect the target skill to the
                candidate's existing technologies and project style.

                Example:

                If the candidate knows Java + Spring Boot + MySQL
                and the target skill is Redis:

                Resource 3 should teach Redis integration with
                Java/Spring Boot, not a generic Redis tutorial.

                If the candidate knows React + Node.js + MongoDB,
                adapt Resource 3 to that stack.

                Prefer high-quality official documentation or
                well-established learning resources.

                Do not claim that the candidate already knows the skill.

                Return ONLY valid JSON:

                {
                  "skill": "string",
                  "resources": [
                    {
                      "title": "What is <skill>?",
                      "purpose": "What this resource teaches",
                      "url": "https://..."
                    },
                    {
                      "title": "How <skill> works",
                      "purpose": "What this resource teaches",
                      "url": "https://..."
                    },
                    {
                      "title": "Using <skill> with <candidate stack>",
                      "purpose": "Stack-specific practical usage",
                      "url": "https://..."
                    }
                  ]
                }
                """;

        String raw =
                geminiClient.generate(
                        prompt,
                        2500
                );

        raw = cleanJson(raw);

        try {

            JsonNode root =
                    objectMapper.readTree(raw);

            JsonNode resources =
                    root.path("resources");

            if (!resources.isArray()
                    || resources.size() < 3) {

                throw new RuntimeException(
                        "Gemini returned fewer than 3 resources."
                );
            }

            Resource[] result =
                    new Resource[3];

            for (int i = 0; i < 3; i++) {

                JsonNode node =
                        resources.get(i);

                result[i] =
                        new Resource(
                                node.path("title")
                                        .asText(),
                                node.path("purpose")
                                        .asText(),
                                node.path("url")
                                        .asText()
                        );
            }

            return new LearningResponse(
                    root.path("skill")
                            .asText(skill),
                    safeExperience,
                    stack.skills(),
                    result
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Unable to parse learning resources.",
                    e
            );
        }
    }

    private String cleanJson(String value) {

        value = value.trim();

        if (value.startsWith("```json")) {
            value =
                    value.substring(
                            7
                    ).trim();
        } else if (value.startsWith("```")) {
            value =
                    value.substring(
                            3
                    ).trim();
        }

        if (value.endsWith("```")) {
            value =
                    value.substring(
                            0,
                            value.length() - 3
                    ).trim();
        }

        return value;
    }

    public record LearningResponse(
            String skill,
            String experience,
            java.util.List<String> candidateSkills,
            Resource[] resources
    ) {}

    public record Resource(
            String title,
            String purpose,
            String url
    ) {}
}
