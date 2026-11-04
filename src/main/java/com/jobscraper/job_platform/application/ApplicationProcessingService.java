package com.jobscraper.job_platform.application;

import com.jobscraper.job_platform.coldmail.ColdMailService;
import com.jobscraper.job_platform.hr.HrContact;
import com.jobscraper.job_platform.hr.HrContactRepository;
import com.jobscraper.job_platform.job.Job;
import com.jobscraper.job_platform.job.JobRepository;
import com.jobscraper.job_platform.resume.ResumeService;
import com.jobscraper.job_platform.resume.CoverLetterService;
import com.jobscraper.job_platform.user.User;
import com.jobscraper.job_platform.user.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ApplicationProcessingService {

    private final JobApplicationRepository applicationRepository;

    private final UserRepository userRepository;

    private final JobRepository jobRepository;

    private final HrContactRepository hrContactRepository;

    private final ResumeService resumeService;

    private final CoverLetterService coverLetterService;

    private final ColdMailService coldMailService;

    private final ObjectMapper objectMapper;


    /*
     * ==========================================================
     * CLAIM APPLICATION
     * ==========================================================
     */

    @Transactional
    public boolean claimApplication(
            Long applicationId
    ) {

        int updated =
                applicationRepository.claimApplication(
                        applicationId
                );

        return updated == 1;
    }


    /*
     * ==========================================================
     * PROCESS APPLICATION
     * ==========================================================
     */

    public void processApplication(
            JobApplication application
    ) {

        Long userId =
                application.getUserId();

        String jobId =
                application.getJobId();


        System.out.println(
                "=========================================="
        );

        System.out.println(
                "PROCESSING APPLICATION"
        );

        System.out.println(
                "APPLICATION ID: "
                        + application.getApplicationId()
        );

        System.out.println(
                "USER ID: "
                        + userId
        );

        System.out.println(
                "JOB ID: "
                        + jobId
        );

        System.out.println(
                "=========================================="
        );


        try {

            /*
             * --------------------------------------------------
             * 1. LOAD REAL USER
             * --------------------------------------------------
             */

            User user =
                    userRepository
                            .findById(userId)
                            .orElseThrow(
                                    () -> new RuntimeException(
                                            "User not found: "
                                                    + userId
                                    )
                            );


            /*
             * --------------------------------------------------
             * 2. LOAD REAL JOB
             * --------------------------------------------------
             */

            Job job =
                    jobRepository
                            .findById(jobId)
                            .orElseThrow(
                                    () -> new RuntimeException(
                                            "Job not found: "
                                                    + jobId
                                    )
                            );


            /*
             * --------------------------------------------------
             * 3. BUILD STRICT CANDIDATE DATA
             * --------------------------------------------------
             */

            List<String> candidateSkills = parseSkillValues(user.getSkills());
            List<String> jobSkills = parseSkillValues(job.getSkills());
            List<String> roleConfirmedSkills = jobSkills.stream()
                    .filter(jobSkill -> candidateSkills.stream().anyMatch(candidate -> sameSkill(candidate, jobSkill)))
                    .distinct()
                    .toList();
            String userData = buildUserData(user, candidateSkills, roleConfirmedSkills);


            /*
             * --------------------------------------------------
             * 4. BUILD JOB DATA
             * --------------------------------------------------
             */

            String jobData =
                    buildJobData(
                            job
                    );


            /*
             * --------------------------------------------------
             * 5. FIND HR
             * --------------------------------------------------
             */

            String hrEmail = null;


            List<HrContact> contacts =
                    hrContactRepository
                            .findByCompanyNameIgnoreCase(
                                    job.getCompany()
                            );


            if (
                    contacts != null
                            && !contacts.isEmpty()
            ) {

                hrEmail =
                        contacts
                                .get(0)
                                .getMailId();


                System.out.println(
                        "HR EMAIL FOUND: "
                                + hrEmail
                );

            } else {

                System.out.println(
                        "NO HR EMAIL FOUND FOR: "
                                + job.getCompany()
                );
            }


            /*
             * --------------------------------------------------
             * 6. GENERATE RESUME
             * --------------------------------------------------
             */

            System.out.println(
                    "STARTING RESUME GENERATION..."
            );


            ResumeService.ResumeGenerationResult resumeResult =
                    resumeService.generateResume(
                            userData,
                            jobData,
                            user.getName(),
                            job.getJobId(),
                            candidateSkills,
                            roleConfirmedSkills
                    );

            CoverLetterService.CoverLetterResult coverLetterResult = coverLetterService.generate(
                    userData, jobData, user.getName(), java.nio.file.Path.of(resumeResult.pdfPath()));


            /*
             * --------------------------------------------------
             * 7. GENERATE COLD MAIL
             * --------------------------------------------------
             */

            System.out.println(
                    "STARTING COLD MAIL GENERATION..."
            );


            ColdMailService.ColdMailResult coldMailResult =
                    coldMailService.generateColdMail(
                            userData,
                            jobData,
                            hrEmail
                    );


            /*
             * --------------------------------------------------
             * 8. SAVE RESULTS
             * --------------------------------------------------
             */

            application.setResumePath(
                    resumeResult.pdfPath()
            );

            application.setCoverLetterPath(coverLetterResult.pdfPath());
            application.setCoverLetterBody(coverLetterResult.body());

            application.setColdMailSubject(
                    coldMailResult.subject()
            );

            application.setColdMailBody(
                    coldMailResult.body()
            );

            application.setHrMailId(
                    hrEmail
            );

            application.setReadyAt(
                    LocalDateTime.now()
            );

            application.setStatus(
                    "READY_FOR_APPROVAL"
            );

            application.setErrorMessage(
                    null
            );


            applicationRepository.save(
                    application
            );


            /*
             * --------------------------------------------------
             * SUCCESS
             * --------------------------------------------------
             */

            System.out.println(
                    "=========================================="
            );

            System.out.println(
                    "APPLICATION READY FOR APPROVAL"
            );

            System.out.println(
                    "APPLICATION ID: "
                            + application.getApplicationId()
            );

            System.out.println(
                    "RESUME: "
                            + resumeResult.pdfPath()
            );

            System.out.println(
                    "HR EMAIL: "
                            + hrEmail
            );

            System.out.println(
                    "=========================================="
            );


        } catch (Exception e) {

            System.err.println(
                    "=========================================="
            );

            System.err.println(
                    "APPLICATION PROCESSING FAILED"
            );

            System.err.println(
                    "APPLICATION ID: "
                            + application.getApplicationId()
            );

            System.err.println(
                    "ERROR: "
                            + e.getMessage()
            );

            System.err.println(
                    "=========================================="
            );


            e.printStackTrace();


            application.setStatus(
                    "FAILED"
            );

            application.setErrorMessage(
                    e.getMessage()
            );

            applicationRepository.save(
                    application
            );
        }
    }


    /*
     * ==========================================================
     * STRICT CANDIDATE DATA
     * ==========================================================
     */

    private String buildUserData(User user, List<String> candidateSkills, List<String> roleConfirmedSkills) {

        return """
                ============================================================
                CANDIDATE_DATA
                ============================================================

                CRITICAL AUTHORITY RULE:

                Everything in this section is real candidate information
                retrieved from the application's User database.

                This is the ONLY authoritative source for candidate facts.

                NEVER copy candidate facts from examples in the prompt.

                NEVER invent:
                - names
                - companies
                - universities
                - GPA/CGPA
                - dates
                - job titles
                - project names
                - technologies
                - metrics
                - percentages
                - user counts
                - record counts
                - achievements

                If a value is empty, it means the candidate did not provide it.

                ------------------------------------------------------------
                NAME
                ------------------------------------------------------------
                Full name: %s
                First name: %s
                Last name: %s

                ------------------------------------------------------------
                EMAIL
                ------------------------------------------------------------
                %s

                ------------------------------------------------------------
                PHONE
                ------------------------------------------------------------
                Country code: %s
                Number: %s

                ------------------------------------------------------------
                PROFESSIONAL TITLE
                ------------------------------------------------------------
                %s

                ------------------------------------------------------------
                LOCATION
                ------------------------------------------------------------
                %s

                ------------------------------------------------------------
                TOTAL EXPERIENCE
                ------------------------------------------------------------
                %s

                ------------------------------------------------------------
                GITHUB
                ------------------------------------------------------------
                %s

                ------------------------------------------------------------
                LINKEDIN
                ------------------------------------------------------------
                %s

                ------------------------------------------------------------
                PORTFOLIO
                ------------------------------------------------------------
                %s

                ------------------------------------------------------------
                EDUCATION
                ------------------------------------------------------------
                %s

                ------------------------------------------------------------
                SKILLS
                ------------------------------------------------------------
                %s

                ALL SKILLS ENTERED OR EXPLICITLY CONFIRMED BY THE CANDIDATE
                ------------------------------------------------------------
                %s

                JOB SKILLS EXPLICITLY CONFIRMED BY THE CANDIDATE
                ------------------------------------------------------------
                %s

                ------------------------------------------------------------
                EXPERIENCE
                ------------------------------------------------------------
                %s

                ------------------------------------------------------------
                PROJECTS
                ------------------------------------------------------------
                %s

                ------------------------------------------------------------
                CERTIFICATIONS
                ------------------------------------------------------------
                %s

                ------------------------------------------------------------
                ACHIEVEMENTS
                ------------------------------------------------------------
                %s

                ------------------------------------------------------------
                OTHER DETAILS
                ------------------------------------------------------------
                %s

                ============================================================
                END CANDIDATE_DATA
                ============================================================
                """.formatted(
                safe(user.getName()),
                safe(user.getFirstName()).isBlank() ? firstNameFromFullName(user.getName()) : user.getFirstName(),
                safe(user.getLastName()).isBlank() ? lastNameFromFullName(user.getName()) : user.getLastName(),
                safe(user.getEmail()),
                safe(user.getCountryCode()),
                safe(user.getPhone()),
                safe(user.getProfessionalTitle()),
                safe(user.getLocation()),
                user.getUserExperience() == null
                        ? "0"
                        : user.getUserExperience(),
                safe(user.getGithubUrl()),
                safe(user.getLinkedinUrl()),
                safe(user.getPortfolioUrl()),
                safe(user.getEducation()),
                safe(user.getSkills()),
                String.join(", ", candidateSkills),
                String.join(", ", roleConfirmedSkills),
                safe(user.getExperience()),
                safe(user.getProjects()),
                safe(user.getCertifications()),
                safe(user.getAchievements()),
                safe(user.getOtherDetails())
        );
    }

    private List<String> parseSkillValues(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        LinkedHashSet<String> values = new LinkedHashSet<>();
        try {
            JsonNode root = objectMapper.readTree(raw);
            collectSkillValues(root, values);
        } catch (Exception ignored) {
            for (String value : raw.split("[,;\\n]")) if (!value.isBlank()) values.add(value.trim());
        }
        return new ArrayList<>(values);
    }

    private void collectSkillValues(JsonNode node, LinkedHashSet<String> values) {
        if (node == null || node.isNull()) return;
        if (node.isTextual()) {
            String value = node.asText().trim();
            if (!value.isBlank()) values.add(value);
        } else if (node.isArray()) {
            node.forEach(child -> collectSkillValues(child, values));
        } else if (node.isObject()) {
            String value = node.path("skill").asText(node.path("name").asText(node.path("value").asText(""))).trim();
            if (!value.isBlank()) values.add(value);
            else node.properties().forEach(entry -> collectSkillValues(entry.getValue(), values));
        }
    }

    private boolean sameSkill(String candidate, String jobSkill) {
        String c = normalizeSkill(candidate);
        String j = normalizeSkill(jobSkill);
        if (c.equals(j)) return true;
        return (c.equals("oop") || c.equals("oops")) && (j.equals("oop") || j.equals("oops"));
    }

    private String normalizeSkill(String value) {
        return value == null ? "" : value.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }

    private String firstNameFromFullName(String fullName) {
        String[] parts = safe(fullName).trim().split("\\s+");
        if (parts.length == 0 || parts[0].isBlank()) return "";
        return parts.length == 1 ? parts[0] : String.join(" ", java.util.Arrays.copyOf(parts, parts.length - 1));
    }

    private String lastNameFromFullName(String fullName) {
        String[] parts = safe(fullName).trim().split("\\s+");
        return parts.length > 1 ? parts[parts.length - 1] : "";
    }


    /*
     * ==========================================================
     * JOB DATA
     * ==========================================================
     */

    private String buildJobData(
            Job job
    ) {

        return """
                ============================================================
                JOB_DATA
                ============================================================

                Job ID:
                %s

                Company:
                %s

                Job Title:
                %s

                Job Description:
                %s

                Required Skills:
                %s

                Experience Level:
                %s

                Location:
                %s

                Salary:
                %s

                Job URL:
                %s

                ============================================================
                END JOB_DATA
                ============================================================
                """.formatted(
                safe(job.getJobId()),
                safe(job.getCompany()),
                safe(job.getTitle()),
                safe(job.getJd()),
                safe(job.getSkills()),
                safe(job.getExperienceLevel()),
                safe(job.getLocation()),
                safe(job.getSalary()),
                safe(job.getJobUrl())
        );
    }


    /*
     * ==========================================================
     * SAFE STRING
     * ==========================================================
     */

    private String safe(
            String value
    ) {

        return value == null
                ? ""
                : value;
    }
}
