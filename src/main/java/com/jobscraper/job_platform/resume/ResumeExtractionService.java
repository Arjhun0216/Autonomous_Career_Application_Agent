package com.jobscraper.job_platform.profile;

import com.jobscraper.job_platform.resume.GeminiClient;
import com.jobscraper.job_platform.user.User;

import lombok.RequiredArgsConstructor;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class ResumeExtractionService {

    private final GeminiClient geminiClient;
    private final ObjectMapper objectMapper;


    public JsonNode extract(
            MultipartFile resume,
            User user) {

        if (
                resume == null ||
                resume.isEmpty()
        ) {

            throw new RuntimeException(
                    "Resume file is empty."
            );
        }


        String filename =
                resume.getOriginalFilename();


        if (
                filename == null ||
                !filename
                        .toLowerCase()
                        .endsWith(".pdf")
        ) {

            throw new RuntimeException(
                    "Only PDF resumes are supported."
            );
        }


        try {

            String resumeText;


            /*
             * ==============================================
             * EXTRACT TEXT FROM PDF
             * ==============================================
             */

            try (
                    PDDocument document =
                            Loader.loadPDF(
                                    resume.getBytes()
                            )
            ) {

                PDFTextStripper stripper =
                        new PDFTextStripper();

                resumeText =
                        stripper.getText(
                                document
                        );
            }


            if (
                    resumeText == null ||
                    resumeText.isBlank()
            ) {

                throw new RuntimeException(
                        "Could not extract text from resume."
                );
            }


            /*
             * ==============================================
             * LIMIT VERY LARGE RESUMES
             * ==============================================
             */

            if (
                    resumeText.length() > 50000
            ) {

                resumeText =
                        resumeText.substring(
                                0,
                                50000
                        );
            }


            /*
             * ==============================================
             * GEMINI
             * ==============================================
             */

            String prompt =
                    buildPrompt(
                            user,
                            resumeText
                    );


            String response =
                    geminiClient.generate(
                            prompt,
                            6000
                    );


            return parseJson(
                    response
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Resume extraction failed: "
                            + e.getMessage(),
                    e
            );
        }
    }


    private String buildPrompt(
            User user,
            String resumeText) {

        return """
                You are a professional resume
                information extraction engine.

                Your task is to extract structured
                candidate profile information from
                the supplied resume.

                You also receive the candidate's
                existing database information.

                IMPORTANT RULES:

                1. Do NOT invent information.
                2. Do NOT infer unsupported facts.
                3. Resume information should be used
                   to enrich the candidate profile.
                4. Existing database email and phone
                   are authoritative when available.
                5. Return JSON ONLY.
                6. If information is unavailable,
                   return an empty value or empty array.
                7. Preserve factual information.
                8. Skills must be individual skill names.
                9. Experience must be structured.
                10. Education must be structured.
                11. Projects must be structured.

                ==========================================
                EXISTING DATABASE USER
                ==========================================

                Name:
                %s

                Email:
                %s

                Phone:
                %s

                Professional Title:
                %s

                Location:
                %s

                Experience:
                %s

                GitHub:
                %s

                LinkedIn:
                %s

                Portfolio:
                %s


                ==========================================
                REQUIRED JSON FORMAT
                ==========================================

                {
                  "personal": {
                    "name": "",
                    "professionalTitle": "",
                    "location": "",
                    "phone": "",
                    "email": "",
                    "userExperience": 0,
                    "githubUrl": "",
                    "linkedinUrl": "",
                    "portfolioUrl": "",
                    "otherLinks": []
                  },

                  "education": [
                    {
                      "degree": "",
                      "institution": "",
                      "location": "",
                      "field": "",
                      "startYear": "",
                      "endYear": "",
                      "score": "",
                      "scoreType": "CGPA"
                    }
                  ],

                  "skills": [],

                  "experience": [
                    {
                      "company": "",
                      "role": "",
                      "location": "",
                      "employmentType": "",
                      "startDate": "",
                      "endDate": "",
                      "current": false,
                      "skills": "",
                      "responsibilities": []
                    }
                  ],

                  "projects": [
                    {
                      "name": "",
                      "organization": "",
                      "techStack": "",
                      "description": "",
                      "githubUrl": "",
                      "liveUrl": "",
                      "contributions": []
                    }
                  ],

                  "additional": {
                    "certifications": [],
                    "achievements": [],
                    "leadership": [],
                    "publications": [],
                    "volunteering": [],
                    "languages": [],
                    "other": ""
                  }
                }


                ==========================================
                RESUME
                ==========================================

                %s
                """
                .formatted(
                        safe(user.getName()),
                        safe(user.getEmail()),
                        safe(user.getPhone()),
                        safe(user.getProfessionalTitle()),
                        safe(user.getLocation()),
                        safeExperience(
                                user.getUserExperience()
                        ),
                        safe(user.getGithubUrl()),
                        safe(user.getLinkedinUrl()),
                        safe(user.getPortfolioUrl()),
                        resumeText
                );
    }


    private JsonNode parseJson(
            String response) {

        try {

            String json =
                    response.trim();


            /*
             * Remove Markdown code fences.
             */

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

                json =
                        json.trim();
            }


            /*
             * If Gemini included text around JSON,
             * isolate the JSON object.
             */

            int start =
                    json.indexOf("{");

            int end =
                    json.lastIndexOf("}");


            if (
                    start >= 0 &&
                    end > start
            ) {

                json =
                        json.substring(
                                start,
                                end + 1
                        );
            }


            return objectMapper.readTree(
                    json
            );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Gemini returned invalid JSON.",
                    e
            );
        }
    }


    private String safe(
            String value) {

        return value == null
                ? ""
                : value;
    }


    private String safeExperience(
            Integer value) {

        return value == null
                ? "0"
                : String.valueOf(value);
    }
}