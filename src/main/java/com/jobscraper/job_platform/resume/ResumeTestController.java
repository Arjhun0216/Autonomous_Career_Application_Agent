package com.jobscraper.job_platform.resume;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/resume")
@RequiredArgsConstructor
public class ResumeTestController {

    private final GeminiClient geminiClient;

    private final ResumeService resumeService;


    // =========================================================
    // GEMINI CONNECTION TEST
    // =========================================================

    @GetMapping("/gemini-test")
    public ResponseEntity<?> testGemini() {

        try {

            String response =
                    geminiClient.generate(
                            """
                            You are testing an API connection.

                            Return ONLY this exact text:

                            GEMINI_CONNECTION_SUCCESS
                            """
                    );

            return ResponseEntity.ok(
                    response
            );

        } catch (Exception e) {

            return ResponseEntity
                    .internalServerError()
                    .body(
                            e.getMessage()
                    );
        }
    }


    // =========================================================
    // FULL RESUME GENERATION TEST
    // =========================================================

    @GetMapping("/test")
    public ResponseEntity<?> testResumeGeneration() {

        try {

            String userData = """
                    Name: Arjun M

                    Professional Title:
                    Computer Science Engineering Student

                    Location:
                    Tamil Nadu, India

                    Education:

                    B.E. Computer Science and Engineering
                    Government College of Engineering Bargur
                    Expected Graduation: 2027

                    Skills:

                    Java
                    Spring Boot
                    React
                    JavaScript
                    Python
                    MySQL
                    MongoDB
                    HTML
                    CSS
                    Machine Learning
                    Git
                    GitHub

                    Experience:

                    Developed and maintained web applications using
                    PHP, MySQL and related technologies.

                    Projects:

                    AI Job Portal:

                    Developed a job platform with React frontend,
                    Spring Boot backend and MySQL database.

                    Implemented job recommendations and application
                    workflows.

                    Recommendation System:

                    Developed a machine learning recommendation system
                    using Python and machine learning techniques.

                    Certifications:

                    AWS Cloud Practitioner Essentials
                    """;


            String jobData = """
                    Job Title:

                    Java Backend Developer


                    Company:

                    Example Technologies


                    Job Description:

                    We are looking for a Java Backend Developer.


                    Requirements:

                    - Java
                    - Spring Boot
                    - REST APIs
                    - MySQL
                    - Git
                    - Backend development
                    - Database management


                    Responsibilities:

                    - Develop backend applications.
                    - Build REST APIs.
                    - Work with relational databases.
                    - Collaborate with development teams.
                    """;


            ResumeService.ResumeGenerationResult result =
        resumeService.generateResume(
                userData,
                jobData,
                "Arjun M",
                "TEST-JOB-001"
        );


            return ResponseEntity.ok(
                    result
            );


        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .internalServerError()
                    .body(
                            e.getMessage()
                    );
        }
    }
}