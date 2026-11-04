package com.jobscraper.job_platform.profile;

import com.jobscraper.job_platform.user.User;
import com.jobscraper.job_platform.user.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;


    // ============================================================
    // UPDATE PROFILE
    // ============================================================

    @Transactional
    public User updateProfile(
            String email,
            ProfileUpdateRequest request) {

        User user = userRepository
                .findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("User not found")
                );


        // --------------------------------------------------
        // PERSONAL DETAILS
        // --------------------------------------------------

        String firstName = request.getFirstName() == null ? "" : request.getFirstName().trim();
        String lastName = request.getLastName() == null ? "" : request.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        if (fullName.isBlank()) {
            fullName = request.getName() == null ? "" : request.getName().trim();
            String[] nameParts = fullName.split("\\s+");
            firstName = nameParts.length == 0 ? "" : nameParts.length == 1 ? nameParts[0]
                    : String.join(" ", java.util.Arrays.copyOf(nameParts, nameParts.length - 1));
            lastName = nameParts.length <= 1 ? "" : nameParts[nameParts.length - 1];
        }
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setName(fullName);
        String countryCode = request.getCountryCode() == null ? "+91" : request.getCountryCode().trim();
        if (!countryCode.matches("\\+\\d{1,4}")) {
            throw new RuntimeException("Country code must look like +91.");
        }
        user.setCountryCode(countryCode);

        String gender = request.getGender();
        if (gender != null && !gender.isBlank()) {
            gender = gender.trim();
            if (!gender.equalsIgnoreCase("Male") && !gender.equalsIgnoreCase("Female")) {
                throw new RuntimeException("Gender must be Male or Female.");
            }
            user.setGender(gender.equalsIgnoreCase("Male") ? "Male" : "Female");
        } else {
            user.setGender(null);
        }

        user.setProfessionalTitle(
                request.getProfessionalTitle()
        );

        user.setLocation(
                request.getLocation()
        );

        String phone = request.getPhone() == null ? "" : request.getPhone().replaceAll("\\D", "");
        user.setPhone(phone.isBlank() ? null : phone);


        // --------------------------------------------------
        // TOTAL WORKING EXPERIENCE
        // --------------------------------------------------

        Integer experience =
                request.getUserExperience();

        if (experience == null) {
            experience = 0;
        }

        if (experience < 0) {

            throw new RuntimeException(
                    "Experience cannot be negative"
            );
        }

        user.setUserExperience(
                experience
        );


        // --------------------------------------------------
        // PROFESSIONAL LINKS
        // --------------------------------------------------

        /*
         * Do NOT change the authenticated user's email
         * from the profile request.
         *
         * Email is the authentication identity.
         */

        user.setGithubUrl(
                request.getGithubUrl()
        );

        user.setLinkedinUrl(
                request.getLinkedinUrl()
        );

        user.setPortfolioUrl(
                request.getPortfolioUrl()
        );


        // --------------------------------------------------
        // PROFILE JSON SECTIONS
        // --------------------------------------------------

        user.setOtherLinks(
                convertToJson(
                        request.getOtherLinks()
                )
        );

        user.setEducation(
                convertToJson(
                        request.getEducation()
                )
        );


        // --------------------------------------------------
        // SKILLS
        // --------------------------------------------------

        /*
         * Skills are normalized before storing.
         *
         * Example:
         *
         * Java
         * JAVA
         * Java
         * java
         *
         * becomes:
         *
         * java
         */

        user.setSkills(
                normalizeSkillsToJson(
                        request.getSkills()
                )
        );


        // --------------------------------------------------
        // EXPERIENCE DETAILS
        // --------------------------------------------------

        user.setExperience(
                convertToJson(
                        request.getExperience()
                )
        );


        // --------------------------------------------------
        // PROJECTS
        // --------------------------------------------------

        user.setProjects(
                convertToJson(
                        request.getProjects()
                )
        );


        // --------------------------------------------------
        // CERTIFICATIONS
        // --------------------------------------------------

        user.setCertifications(
                convertToJson(
                        request.getCertifications()
                )
        );


        // --------------------------------------------------
        // ACHIEVEMENTS
        // --------------------------------------------------

        user.setAchievements(
                convertToJson(
                        request.getAchievements()
                )
        );


        // --------------------------------------------------
        // OTHER DETAILS
        // --------------------------------------------------

        user.setOtherDetails(
                convertToJson(
                        request.getOtherDetails()
                )
        );

        // --------------------------------------------------
        // APPLICATION / COMPLIANCE DETAILS
        // --------------------------------------------------

        String workAuthorization = request.getWorkAuthorization() == null ? "" : request.getWorkAuthorization().trim();
        if (workAuthorization.isBlank()) {
            throw new RuntimeException("Work authorization status is required to complete your profile.");
        }
        if (!Set.of("AUTHORIZED", "NOT_AUTHORIZED").contains(workAuthorization)) {
            throw new RuntimeException("Select a valid work authorization status.");
        }
        user.setWorkAuthorization(workAuthorization);
        user.setWorkAuthorizationCountry(request.getWorkAuthorizationCountry());
        user.setVisaSponsorship(request.getVisaSponsorship());
        user.setMilitaryStatus(request.getMilitaryStatus());
        user.setGovernmentProjectExperience(request.getGovernmentProjectExperience());
        user.setGovernmentProjectDetails(request.getGovernmentProjectDetails());


        // --------------------------------------------------
        // PROFILE COMPLETED
        // --------------------------------------------------

        user.setRegistrationCompleted(
                true
        );


        return userRepository.save(user);
    }


    // ============================================================
    // GET CURRENT USER PROFILE
    // ============================================================

    public User getProfile(String email) {

        return userRepository
                .findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException(
                                "User profile not found"
                        )
                );
    }


    // ============================================================
    // GENERIC JSON CONVERSION
    // ============================================================

    private String convertToJson(Object value) {

        if (value == null) {
            return null;
        }

        return objectMapper.writeValueAsString(
                value
        );
    }


    // ============================================================
    // SKILL NORMALIZATION
    // ============================================================

    private String normalizeSkillsToJson(
            Object value) {

        if (value == null) {
            return null;
        }


        // --------------------------------------------------
        // Convert incoming value to List
        // --------------------------------------------------

        List<?> inputSkills;

        try {

            inputSkills =
                    objectMapper.convertValue(
                            value,
                            List.class
                    );

        } catch (Exception e) {

            throw new RuntimeException(
                    "Invalid skills format"
            );
        }


        // --------------------------------------------------
        // Normalize + Remove duplicates
        // --------------------------------------------------

        /*
         * LinkedHashSet:
         *
         * 1. Removes duplicates
         * 2. Keeps original order
         */

        Set<String> normalizedSkills =
                new LinkedHashSet<>();


        for (Object skillObject :
                inputSkills) {

            if (skillObject == null) {
                continue;
            }


            String skill =
                    skillObject
                            .toString()
                            .trim()
                            .toLowerCase();


            if (!skill.isBlank()) {

                normalizedSkills.add(
                        skill
                );
            }
        }


        // --------------------------------------------------
        // Convert normalized skills to JSON
        // --------------------------------------------------

        return objectMapper.writeValueAsString(
                new ArrayList<>(
                        normalizedSkills
                )
        );
    }
}
