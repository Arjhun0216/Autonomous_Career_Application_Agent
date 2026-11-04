package com.jobscraper.job_platform.recommendation;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recommendations")
@RequiredArgsConstructor
public class RecommendationController {

    private final RecommendationService recommendationService;


    @GetMapping
    public ResponseEntity<List<RecommendationResponse>>
    getRecommendations(
            Authentication authentication) {

        String email =
                authentication.getName();


        List<RecommendationResponse> recommendations =
                recommendationService
                        .getRecommendations(email);


        return ResponseEntity.ok(
                recommendations
        );
    }


    @GetMapping("/skills")
    public ResponseEntity<List<RecommendationService.SkillRecommendation>>
    getSkillRecommendations(Authentication authentication) {

        String email = authentication.getName();

        return ResponseEntity.ok(
                recommendationService.getSkillRecommendations(email)
        );
    }
}