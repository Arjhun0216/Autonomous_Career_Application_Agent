package com.jobscraper.job_platform.profile;

import com.jobscraper.job_platform.user.User;
import com.jobscraper.job_platform.user.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.Authentication;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import tools.jackson.databind.JsonNode;


@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ResumeExtractionController {

    private final ResumeExtractionService resumeExtractionService;

    private final UserRepository userRepository;


    @PostMapping(
            value = "/extract-resume",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public ResponseEntity<?> extractResume(

            @RequestParam("resume")
            MultipartFile resume,

            Authentication authentication) {

        try {

            if (
                    authentication == null
            ) {

                return ResponseEntity
                        .status(401)
                        .body(
                                "Authentication required."
                        );
            }


            String email =
                    authentication.getName();


            User user =
                    userRepository
                            .findByEmail(email)
                            .orElseThrow(
                                    () ->
                                            new RuntimeException(
                                                    "User not found."
                                            )
                            );


            JsonNode result =
                    resumeExtractionService.extract(
                            resume,
                            user
                    );


            return ResponseEntity.ok(
                    result
            );


        } catch (Exception e) {

            e.printStackTrace();

            return ResponseEntity
                    .badRequest()
                    .body(
                            e.getMessage() == null
                                    ? "Resume extraction failed."
                                    : e.getMessage()
                    );
        }
    }
}