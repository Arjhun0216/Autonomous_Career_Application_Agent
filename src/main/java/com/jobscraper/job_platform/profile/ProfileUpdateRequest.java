package com.jobscraper.job_platform.profile;

import lombok.Data;

@Data
public class ProfileUpdateRequest {

    private String name;
    private String firstName;
    private String lastName;
    private String countryCode;
    private String gender;
    private String professionalTitle;
    private String location;
    private String phone;
    private String email;

    /*
     * Total working experience in years.
     * Pure number.
     */
    private Integer userExperience;

    private String githubUrl;
    private String linkedinUrl;
    private String portfolioUrl;

    private Object otherLinks;
    private Object education;
    private Object skills;
    private Object experience;
    private Object projects;

    private Object certifications;
    private Object achievements;
    private Object otherDetails;

    private String workAuthorization;
    private String workAuthorizationCountry;
    private String visaSponsorship;
    private String militaryStatus;
    private String governmentProjectExperience;
    private String governmentProjectDetails;
}
