package com.jobscraper.job_platform.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "first_name", length = 255)
    private String firstName;

    @Column(name = "last_name", length = 255)
    private String lastName;

    /** Legacy full-name field retained for older profile and application flows. */
    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "country_code", length = 12, nullable = false)
    @Builder.Default
    private String countryCode = "+91";

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(unique = true)
    private String phone;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "professional_title")
    private String professionalTitle;

    private String location;

    /*
     * Total working experience in years.
     *
     * Examples:
     * 0
     * 1
     * 2
     * 5
     * 10
     * 20
     *
     * Stored as a pure number.
     */
    @Builder.Default
    @Column(name = "user_experience", nullable = false)
    private Integer userExperience = 0;

    @Column(name = "github_url")
    private String githubUrl;

    @Column(name = "linkedin_url")
    private String linkedinUrl;

    @Column(name = "portfolio_url")
    private String portfolioUrl;

    /*
     * Profile JSON data
     *
     * Stored as LONGTEXT instead of MySQL/MariaDB JSON
     * to avoid Hibernate JSON CAST compatibility issues.
     */

    @Column(name = "other_links", columnDefinition = "LONGTEXT")
    private String otherLinks;

    @Column(name = "education", columnDefinition = "LONGTEXT")
    private String education;

    @Column(name = "skills", columnDefinition = "LONGTEXT")
    private String skills;

    @Column(name = "experience", columnDefinition = "LONGTEXT")
    private String experience;

    @Column(name = "projects", columnDefinition = "LONGTEXT")
    private String projects;

    @Column(name = "certifications", columnDefinition = "LONGTEXT")
    private String certifications;

    @Column(name = "achievements", columnDefinition = "LONGTEXT")
    private String achievements;

    @Column(name = "other_details", columnDefinition = "LONGTEXT")
    private String otherDetails;

    @Column(name = "work_authorization", length = 100)
    private String workAuthorization;

    @Column(name = "work_authorization_country", length = 100)
    private String workAuthorizationCountry;

    @Column(name = "visa_sponsorship", length = 100)
    private String visaSponsorship;

    @Column(name = "military_status", length = 100)
    private String militaryStatus;

    @Column(name = "government_project_experience", length = 20)
    private String governmentProjectExperience;

    @Column(name = "government_project_details", columnDefinition = "LONGTEXT")
    private String governmentProjectDetails;

    @Builder.Default
    @Column(name = "registration_completed")
    private boolean registrationCompleted = false;

    @Column(name = "gmail_connected", nullable = false)
@Builder.Default
private boolean gmailConnected = false;

@Column(name = "gmail_refresh_token", columnDefinition = "TEXT")
private String gmailRefreshToken;

@Column(name = "gmail_email")
private String gmailEmail;

    @Builder.Default
    @Column(name = "auth_provider", nullable = false)
    private String authProvider = "LOCAL";

    @Column(name = "provider_user_id", unique = true)
    private String providerUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    @CreationTimestamp
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
