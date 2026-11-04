package com.jobscraper.job_platform.recommendation;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import tools.jackson.databind.ObjectMapper;

import com.jobscraper.job_platform.job.Job;
import com.jobscraper.job_platform.job.JobRepository;
import com.jobscraper.job_platform.user.User;
import com.jobscraper.job_platform.user.UserRepository;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;


    public List<RecommendationResponse> getRecommendations(
            String email) {

        // ------------------------------------------------
        // 1. Get logged-in user
        // ------------------------------------------------

        User user = userRepository
                .findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException(
                                "User not found"
                        )
                );


        // ------------------------------------------------
        // 2. Extract user skills
        // ------------------------------------------------

        Set<String> userSkills =
                parseUserSkills(user.getSkills());


        // ------------------------------------------------
        // 3. User experience
        // ------------------------------------------------

        int userExperience =
                user.getUserExperience() == null
                        ? 0
                        : user.getUserExperience();


        // ------------------------------------------------
        // 4. Create semantic text
        // ------------------------------------------------

        String userText =
                buildUserEmbeddingText(
                        user,
                        userSkills
                );


        // ------------------------------------------------
        // 5. Generate user embedding
        // ------------------------------------------------

        List<Double> userEmbedding =
                embeddingClient.generateEmbedding(
                        userText
                );

        System.out.println("========================================");
        System.out.println("RECOMMENDATION DEBUG");
        System.out.println("User Email: " + email);
        System.out.println("User Skills: " + userSkills);
        System.out.println("User Experience: " + userExperience);
        System.out.println(
                "User Embedding Dimension: "
                + (
                    userEmbedding == null
                            ? "NULL"
                            : userEmbedding.size()
                )
        );
        System.out.println("User Embedding Text:");
        System.out.println(userText);
        System.out.println("========================================");


        // ------------------------------------------------
        // 6. Load jobs
        // ------------------------------------------------

        List<Job> jobs =
                jobRepository.findAll();


        // ------------------------------------------------
        // 7. Calculate recommendation scores
        // ------------------------------------------------

        List<RecommendationResponse> recommendations =
                jobs.stream()
                        // Ignore empty placeholder rows left in the jobs table, but keep every
                        // real opening so missing/low embeddings cannot make Home appear empty.
                        .filter(job -> job.getJobId() != null && !job.getJobId().isBlank())
                        .filter(job -> job.getTitle() != null && !job.getTitle().isBlank())

                        .map(job ->
                                calculateRecommendation(
                                        job,
                                        userSkills,
                                        userExperience,
                                        userEmbedding
                                )
                        )

                        // ------------------------------------------------
                        // Sort highest score first. Lower matches remain visible below
                        // recommendations instead of being discarded entirely.
                        // ------------------------------------------------
                        .sorted(
                                Comparator.comparingDouble(
                                        RecommendationResponse
                                                ::getMatchScore
                                ).reversed()
                        )

                        .collect(Collectors.toList());


        return recommendations;
    }


    // ====================================================
    // MAIN RECOMMENDATION CALCULATION
    // ====================================================

    private RecommendationResponse calculateRecommendation(
            Job job,
            Set<String> userSkills,
            int userExperience,
            List<Double> userEmbedding) {


        // ------------------------------------------------
        // Semantic similarity
        // ------------------------------------------------

        double semanticSimilarity =
                calculateSemanticSimilarity(
                        userEmbedding,
                        job.getEmbedding()
                );

        System.out.println(
                job.getJobId()
                + " | "
                + job.getTitle()
                + " | semantic="
                + semanticSimilarity
        );


        // ------------------------------------------------
        // Explicit skill overlap
        // ------------------------------------------------

        double skillOverlap =
                calculateSkillOverlap(
                        userSkills,
                        job.getSkills()
                );


        // ------------------------------------------------
        // Experience compatibility
        // ------------------------------------------------

        double experienceScore =
                calculateExperienceScore(
                        userExperience,
                        job.getExperienceLevel()
                );


        // ------------------------------------------------
        // FINAL SCORE
        //
        // 56% semantic
        // 24% skill overlap
        // 20% experience
        // ------------------------------------------------

        double finalScore =
                (0.56 * semanticSimilarity)
                +
                (0.24 * skillOverlap)
                +
                (0.20 * experienceScore);


        // ------------------------------------------------
        // Convert 0-1 to 0-100
        // ------------------------------------------------

        double matchScore =
                finalScore * 100.0;


        // ------------------------------------------------
        // Round to 2 decimal places
        // ------------------------------------------------

        matchScore =
                Math.round(
                        matchScore * 100.0
                ) / 100.0;


        // ------------------------------------------------
        System.out.println(
                job.getJobId()
                + " | "
                + job.getTitle()
                + " | score="
                + matchScore
                + "% | INCLUDED"
        );


        return new RecommendationResponse(
                job.getJobId(),
                job.getTitle(),
                job.getCompany(),
                job.getLocation(),
                job.getSalary(),
                job.getJobUrl(),
                matchScore
        );
    }



    // ====================================================
    // SKILL RECOMMENDATIONS
    // ====================================================

    /**
     * Returns skills extracted from ALL jobs, ranked using the same
     * recommendation score used by the home page. This intentionally
     * does not use the old low-match (<50%) gap logic.
     *
     * A skill already present in the user's profile is returned as
     * known=true so the UI can ask how deeply the user knows it.
     */
    public List<SkillRecommendation> getSkillRecommendations(
            String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Set<String> userSkills = parseUserSkills(user.getSkills());
        int userExperience = user.getUserExperience() == null
                ? 0
                : user.getUserExperience();

        List<Double> userEmbedding = embeddingClient.generateEmbedding(
                buildUserEmbeddingText(user, userSkills)
        );

        Map<String, SkillAccumulator> skills = new LinkedHashMap<>();

        for (Job job : jobRepository.findAll()) {

            double score = calculateRawRecommendationScore(
                    job, userSkills, userExperience, userEmbedding
            );

            if (job.getSkills() == null || job.getSkills().isBlank()) {
                continue;
            }

            for (String skill : job.getSkills().split(",")) {
                String clean = skill == null ? "" : skill.trim();
                String normalized = normalizeSkill(clean);

                if (normalized.isBlank()) {
                    continue;
                }

                SkillAccumulator current = skills.get(normalized);
                if (current == null) {
                    skills.put(normalized, new SkillAccumulator(
                            clean, 1, score
                    ));
                } else {
                    skills.put(normalized, new SkillAccumulator(
                            current.displayName(),
                            current.jobCount() + 1,
                            Math.max(current.relevanceScore(), score)
                    ));
                }
            }
        }

        return skills.values().stream()
                // This page is a gap/recommendation list: once a skill is in
                // the user's profile (including a newly confirmed skill), do
                // not recommend it again on the next refresh.
                .filter(skill -> !userSkills.contains(
                        normalizeSkill(skill.displayName())
                ))
                .sorted(Comparator
                        .comparingDouble(SkillAccumulator::relevanceScore)
                        .reversed()
                        .thenComparing(
                                SkillAccumulator::jobCount,
                                Comparator.reverseOrder()
                        ))
                .map(skill -> new SkillRecommendation(
                        skill.displayName(),
                        Math.round(skill.relevanceScore() * 100.0) / 100.0,
                        skill.jobCount(),
                        userSkills.contains(
                                normalizeSkill(skill.displayName())
                        )
                ))
                .collect(Collectors.toList());
    }

    private double calculateRawRecommendationScore(
            Job job,
            Set<String> userSkills,
            int userExperience,
            List<Double> userEmbedding) {

        double semantic = calculateSemanticSimilarity(
                userEmbedding, job.getEmbedding()
        );

        double skillOverlap = calculateSkillOverlap(
                userSkills, job.getSkills()
        );

        double experience = calculateExperienceScore(
                userExperience, job.getExperienceLevel()
        );

        return ((0.56 * semantic)
                + (0.24 * skillOverlap)
                + (0.20 * experience)) * 100.0;
    }

    private String normalizeSkill(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }

    private record SkillAccumulator(
            String displayName,
            int jobCount,
            double relevanceScore
    ) {}

    public record SkillRecommendation(
            String skill,
            double relevanceScore,
            int jobCount,
            boolean known
    ) {}

    // ====================================================
    // USER SKILLS
    // ====================================================

    private Set<String> parseUserSkills(
            String skillsJson) {

        if (skillsJson == null ||
                skillsJson.isBlank()) {

            return new LinkedHashSet<>();
        }

        try {

            List<?> skills =
                    objectMapper.readValue(
                            skillsJson,
                            List.class
                    );

            return skills.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(String::trim)
                    .map(String::toLowerCase)
                    .filter(skill ->
                            !skill.isBlank()
                    )
                    .collect(
                            Collectors.toCollection(
                                    LinkedHashSet::new
                            )
                    );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Invalid user skills data"
            );
        }
    }


    // ====================================================
    // USER EMBEDDING TEXT
    // ====================================================

    private String buildUserEmbeddingText(
            User user,
            Set<String> skills) {

        String title =
                user.getProfessionalTitle();

        String skillText =
                String.join(
                        ", ",
                        skills
                );

        return
                "Professional Title: "
                + safe(title)
                + "\n"
                + "Skills: "
                + skillText;
    }


    // ====================================================
    // SKILL OVERLAP
    // ====================================================

    private double calculateSkillOverlap(
            Set<String> userSkills,
            String jobSkills) {

        if (userSkills.isEmpty() ||
                jobSkills == null ||
                jobSkills.isBlank()) {

            return 0.0;
        }


        Set<String> requiredSkills =
                Arrays.stream(
                        jobSkills.split(",")
                )
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(skill ->
                        !skill.isBlank()
                )
                .collect(
                        Collectors.toSet()
                );


        if (requiredSkills.isEmpty()) {
            return 0.0;
        }


        long matchingSkills =
                requiredSkills.stream()
                        .filter(userSkills::contains)
                        .count();


        /*
         * Skill match is measured against the job requirements.
         *
         * matching required skills
         * ------------------------
         * total required skills
         */
        return Math.min(
                1.0,
                (double) matchingSkills
                        / requiredSkills.size()
        );
    }


    // ====================================================
    // EXPERIENCE SCORE
    // ====================================================

    private double calculateExperienceScore(
            int userExperience,
            String jobExperience) {

        if (jobExperience == null ||
                jobExperience.isBlank()) {

            return 0.0;
        }


        int requiredExperience;

        try {

            requiredExperience =
                    Integer.parseInt(
                            jobExperience.trim()
                    );

        } catch (NumberFormatException e) {

            return 0.0;
        }


        // User meets/exceeds requirement

        if (userExperience >= requiredExperience) {
            return 1.0;
        }


        // Exactly one year below

        if (userExperience ==
                requiredExperience - 1) {

            return 0.5;
        }


        // More than one year below

        return 0.0;
    }


    // ====================================================
    // COSINE SIMILARITY
    // ====================================================

    private double calculateSemanticSimilarity(
            List<Double> userEmbedding,
            String jobEmbeddingText) {


        // No user vector

        if (userEmbedding == null ||
                userEmbedding.isEmpty()) {

            return 0.0;
        }


        // No job vector

        if (jobEmbeddingText == null ||
                jobEmbeddingText.isBlank()) {

            return 0.0;
        }


        List<Double> jobEmbedding =
                parseEmbedding(
                        jobEmbeddingText
                );


        // Invalid job vector

        if (jobEmbedding.isEmpty()) {
            return 0.0;
        }


        // Different dimensions

        if (userEmbedding.size() !=
                jobEmbedding.size()) {

            return 0.0;
        }


        double dotProduct = 0.0;
        double userMagnitude = 0.0;
        double jobMagnitude = 0.0;


        for (int i = 0;
             i < userEmbedding.size();
             i++) {

            double userValue =
                    userEmbedding.get(i);

            double jobValue =
                    jobEmbedding.get(i);

            dotProduct +=
                    userValue * jobValue;

            userMagnitude +=
                    userValue * userValue;

            jobMagnitude +=
                    jobValue * jobValue;
        }


        if (userMagnitude == 0 ||
                jobMagnitude == 0) {

            return 0.0;
        }


        double cosine =
                dotProduct
                /
                (
                        Math.sqrt(userMagnitude)
                        *
                        Math.sqrt(jobMagnitude)
                );


        /*
         * Convert possible [-1,1]
         * value to usable [0,1].
         */

        return Math.max(
                0.0,
                Math.min(
                        1.0,
                        cosine
                )
        );
    }


    // ====================================================
    // EMBEDDING PARSER
    // ====================================================

    private List<Double> parseEmbedding(
            String embeddingText) {

        try {

            String cleaned =
                    embeddingText
                            .trim()
                            .replace("[", "")
                            .replace("]", "");


            if (cleaned.isBlank()) {
                return Collections.emptyList();
            }


            String[] values =
                    cleaned.split(",");


            List<Double> embedding =
                    new ArrayList<>();


            for (String value : values) {

                embedding.add(
                        Double.parseDouble(
                                value.trim()
                        )
                );
            }


            return embedding;

        } catch (Exception e) {

            /*
             * This is important right now because
             * your current job embeddings are only
             * temporary/sample values.
             */

            return Collections.emptyList();
        }
    }


    private String safe(String value) {

        return value == null
                ? ""
                : value.trim();
    }
}
