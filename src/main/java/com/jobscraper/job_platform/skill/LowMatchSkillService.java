package com.jobscraper.job_platform.skill;

import com.jobscraper.job_platform.job.Job;
import com.jobscraper.job_platform.job.JobRepository;
import com.jobscraper.job_platform.recommendation.EmbeddingClient;
import com.jobscraper.job_platform.user.User;
import com.jobscraper.job_platform.user.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LowMatchSkillService {

    private static final double LOW_MATCH_THRESHOLD = 50.0;

    private final UserRepository userRepository;
    private final JobRepository jobRepository;
    private final EmbeddingClient embeddingClient;
    private final ObjectMapper objectMapper;

    /*
     * Existing global endpoint.
     *
     * It now uses the requested <50% threshold.
     */
    public MissingSkillResponse findMissingSkills(
            String email
    ) {

        User user =
                userRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User not found"
                                )
                        );

        Set<String> userSkills =
                parseUserSkills(user.getSkills());

        int userExperience =
                user.getUserExperience() == null
                        ? 0
                        : user.getUserExperience();

        List<Double> userEmbedding =
                generateUserEmbedding(
                        user,
                        userSkills
                );

        Map<String, SkillInfo> missingSkills =
                new LinkedHashMap<>();

        List<JobSkillGap> lowMatchJobs =
                new ArrayList<>();

        for (Job job : jobRepository.findAll()) {

            double score =
                    calculateRecommendationScore(
                            job,
                            userSkills,
                            userExperience,
                            userEmbedding
                    );

            if (score >= LOW_MATCH_THRESHOLD) {
                continue;
            }

            Set<String> missing =
                    getMissingSkills(
                            userSkills,
                            job.getSkills()
                    );

            if (missing.isEmpty()) {
                continue;
            }

            for (String skill : missing) {

                String normalized =
                        normalize(skill);

                SkillInfo existing =
                        missingSkills.get(normalized);

                if (existing == null) {

                    missingSkills.put(
                            normalized,
                            new SkillInfo(
                                    skill,
                                    1,
                                    new ArrayList<>(
                                            List.of(
                                                    job.getJobId()
                                            )
                                    )
                            )
                    );

                } else {

                    List<String> ids =
                            new ArrayList<>(
                                    existing.jobIds()
                            );

                    if (!ids.contains(
                            job.getJobId()
                    )) {
                        ids.add(job.getJobId());
                    }

                    missingSkills.put(
                            normalized,
                            new SkillInfo(
                                    existing.skill(),
                                    existing.jobCount() + 1,
                                    ids
                            )
                    );
                }
            }

            lowMatchJobs.add(
                    new JobSkillGap(
                            job.getJobId(),
                            job.getTitle(),
                            job.getCompany(),
                            score,
                            new ArrayList<>(missing)
                    )
            );
        }

        return new MissingSkillResponse(
                LOW_MATCH_THRESHOLD,
                new ArrayList<>(
                        missingSkills.values()
                ),
                lowMatchJobs
        );
    }

    /*
     * Job-specific verification.
     *
     * This is what the Job Details page uses.
     */
    public JobSkillVerification verifyJobSkills(
            String email,
            String jobId
    ) {

        User user =
                userRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User not found"
                                )
                        );

        Job job =
                jobRepository.findById(jobId)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Job not found: "
                                                + jobId
                                )
                        );

        Set<String> userSkills =
                parseUserSkills(user.getSkills());

        Set<String> jobSkills =
                parseJobSkills(job.getSkills());

        int experience =
                user.getUserExperience() == null
                        ? 0
                        : user.getUserExperience();

        List<Double> userEmbedding =
                generateUserEmbedding(
                        user,
                        userSkills
                );

        double score =
                calculateRecommendationScore(
                        job,
                        userSkills,
                        experience,
                        userEmbedding
                );

        List<String> missing =
                getMissingSkills(
                        userSkills,
                        job.getSkills()
                ).stream().toList();

        return new JobSkillVerification(
                job.getJobId(),
                job.getTitle(),
                score,
                score < LOW_MATCH_THRESHOLD,
                new ArrayList<>(jobSkills),
                new ArrayList<>(userSkills),
                missing
        );
    }

    /*
     * Add a skill to users.skills.
     */
    @Transactional
    public UpdatedSkillsResponse confirmSkill(
            String email,
            String skill
    ) {

        if (skill == null || skill.isBlank()) {
            throw new RuntimeException(
                    "Skill is required"
            );
        }

        User user =
                userRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User not found"
                                )
                        );

        List<String> skills =
                parseUserSkillList(
                        user.getSkills()
                );

        String normalized =
                normalize(skill);

        boolean exists =
                skills.stream()
                        .anyMatch(
                                item ->
                                        normalize(item)
                                                .equals(normalized)
                        );

        if (!exists) {
            // Keep User.skills consistent with ProfileService: normalized values only.
            skills.add(normalized);
        }

        try {

            user.setSkills(
                    objectMapper.writeValueAsString(
                            skills
                    )
            );

            userRepository.saveAndFlush(user);

        } catch (Exception e) {

            throw new RuntimeException(
                    "Unable to update user skills",
                    e
            );
        }

        return new UpdatedSkillsResponse(
                skill.trim(),
                skills,
                !exists
        );
    }

    public Set<String> getMissingSkills(
            Set<String> userSkills,
            String jobSkills
    ) {

        return parseJobSkills(jobSkills)
                .stream()
                .filter(
                        skill ->
                                !userSkills.contains(
                                        normalize(skill)
                                )
                )
                .collect(
                        Collectors.toCollection(
                                LinkedHashSet::new
                        )
                );
    }

    public UserStack getUserStack(
            String email
    ) {

        User user =
                userRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User not found"
                                )
                        );

        return new UserStack(
                safe(user.getProfessionalTitle()),
                parseUserSkillList(
                        user.getSkills()
                ),
                safe(user.getExperience()),
                safe(user.getProjects())
        );
    }

    private List<Double> generateUserEmbedding(
            User user,
            Set<String> skills
    ) {

        String text =
                "Professional Title: "
                        + safe(user.getProfessionalTitle())
                        + "\nSkills: "
                        + String.join(", ", skills);

        return embeddingClient.generateEmbedding(text);
    }

    private double calculateRecommendationScore(
            Job job,
            Set<String> userSkills,
            int userExperience,
            List<Double> userEmbedding
    ) {

        double semantic =
                calculateSemanticSimilarity(
                        userEmbedding,
                        job.getEmbedding()
                );

        double skillOverlap =
                calculateSkillOverlap(
                        userSkills,
                        job.getSkills()
                );

        double experience =
                calculateExperienceScore(
                        userExperience,
                        job.getExperienceLevel()
                );

        return Math.round(
                (
                        (0.56 * semantic)
                        + (0.24 * skillOverlap)
                        + (0.20 * experience)
                ) * 10000.0
        ) / 100.0;
    }

    /*
     * Skill overlap is measured against JOB REQUIREMENTS,
     * not the user's skill count.
     *
     * This makes the score meaningful for gap detection.
     */
    private double calculateSkillOverlap(
            Set<String> userSkills,
            String jobSkills
    ) {

        Set<String> required =
                parseJobSkills(jobSkills);

        if (required.isEmpty()) {
            return 0.0;
        }

        long matches =
                required.stream()
                        .filter(userSkills::contains)
                        .count();

        return (double) matches / required.size();
    }

    private double calculateExperienceScore(
            int userExperience,
            String jobExperience
    ) {

        if (jobExperience == null
                || jobExperience.isBlank()) {
            return 0.0;
        }

        try {

            int required =
                    Integer.parseInt(
                            jobExperience.trim()
                    );

            if (userExperience >= required) {
                return 1.0;
            }

            if (userExperience ==
                    required - 1) {
                return 0.5;
            }

            return 0.0;

        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private double calculateSemanticSimilarity(
            List<Double> userEmbedding,
            String jobEmbeddingText
    ) {

        if (userEmbedding == null
                || userEmbedding.isEmpty()
                || jobEmbeddingText == null
                || jobEmbeddingText.isBlank()) {
            return 0.0;
        }

        List<Double> jobEmbedding =
                parseEmbedding(jobEmbeddingText);

        if (jobEmbedding.isEmpty()
                || userEmbedding.size()
                != jobEmbedding.size()) {
            return 0.0;
        }

        double dot = 0.0;
        double userMagnitude = 0.0;
        double jobMagnitude = 0.0;

        for (int i = 0;
             i < userEmbedding.size();
             i++) {

            double a = userEmbedding.get(i);
            double b = jobEmbedding.get(i);

            dot += a * b;
            userMagnitude += a * a;
            jobMagnitude += b * b;
        }

        if (userMagnitude == 0
                || jobMagnitude == 0) {
            return 0.0;
        }

        double cosine =
                dot
                        /
                        (
                                Math.sqrt(userMagnitude)
                                        *
                                Math.sqrt(jobMagnitude)
                        );

        return Math.max(
                0.0,
                Math.min(1.0, cosine)
        );
    }

    private List<Double> parseEmbedding(
            String embeddingText
    ) {

        try {

            List<?> raw =
                    objectMapper.readValue(
                            embeddingText,
                            List.class
                    );

            List<Double> result =
                    new ArrayList<>();

            for (Object value : raw) {

                if (value != null) {
                    result.add(
                            Double.parseDouble(
                                    value.toString()
                            )
                    );
                }
            }

            return result;

        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private Set<String> parseUserSkills(
            String skillsJson
    ) {

        return new LinkedHashSet<>(
                parseUserSkillList(skillsJson)
                        .stream()
                        .map(this::normalize)
                        .filter(
                                skill ->
                                        !skill.isBlank()
                        )
                        .toList()
        );
    }

    private List<String> parseUserSkillList(
            String skillsJson
    ) {

        if (skillsJson == null
                || skillsJson.isBlank()) {
            return new ArrayList<>();
        }

        try {

            List<?> values =
                    objectMapper.readValue(
                            skillsJson,
                            List.class
                    );

            return values.stream()
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(
                            value ->
                                    !value.isBlank()
                    )
                    .collect(
                            Collectors.toCollection(
                                    ArrayList::new
                            )
                    );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Invalid user skills data",
                    e
            );
        }
    }

    private Set<String> parseJobSkills(
            String skills
    ) {

        if (skills == null
                || skills.isBlank()) {
            return new LinkedHashSet<>();
        }

        return Arrays.stream(
                        skills.split(",")
                )
                .map(String::trim)
                .filter(
                        value ->
                                !value.isBlank()
                )
                .map(this::normalize)
                .collect(
                        Collectors.toCollection(
                                LinkedHashSet::new
                        )
                );
    }

    private List<Double> parseEmbeddingText(
            String text
    ) {
        return parseEmbedding(text);
    }

    private String normalize(
            String value
    ) {

        return value == null
                ? ""
                : value.trim()
                        .toLowerCase(Locale.ROOT);
    }

    private String safe(
            String value
    ) {

        return value == null
                ? ""
                : value;
    }

    public record SkillInfo(
            String skill,
            int jobCount,
            List<String> jobIds
    ) {}

    public record JobSkillGap(
            String jobId,
            String title,
            String company,
            double matchScore,
            List<String> missingSkills
    ) {}

    public record MissingSkillResponse(
            double threshold,
            List<SkillInfo> missingSkills,
            List<JobSkillGap> lowMatchJobs
    ) {}

    public record JobSkillVerification(
            String jobId,
            String title,
            double matchScore,
            boolean lowMatch,
            List<String> jobSkills,
            List<String> userSkills,
            List<String> missingSkills
    ) {}

    public record UpdatedSkillsResponse(
            String addedSkill,
            List<String> skills,
            boolean added
    ) {}

    public record UserStack(
            String professionalTitle,
            List<String> skills,
            String experience,
            String projects
    ) {}
}
